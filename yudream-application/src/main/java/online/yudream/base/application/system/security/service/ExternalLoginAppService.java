package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.cmd.ExternalLoginProviderSaveCmd;
import online.yudream.base.application.system.security.dto.*;
import online.yudream.base.application.system.user.dto.UserLoginDTO;
import online.yudream.base.application.system.user.dto.UserProfileDTO;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ExternalAccount;
import online.yudream.base.domain.system.security.aggregate.ExternalLoginProvider;
import online.yudream.base.domain.system.security.repo.ExternalAccountRepo;
import online.yudream.base.domain.system.security.repo.ExternalLoginProviderRepo;
import online.yudream.base.domain.system.security.service.ExternalLoginGateway;
import online.yudream.base.domain.system.security.service.ExternalLoginTicketStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExternalLoginAppService {
    private final ExternalLoginProviderRepo providerRepo;
    private final ExternalAccountRepo accountRepo;
    private final ExternalLoginGateway gateway;
    private final UserAppService userAppService;
    private final LoginTokenAppService loginTokenAppService;
    private final ExternalLoginTicketStore ticketStore;
    private final ExternalLoginBindingAppService bindingAppService;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public ExternalLoginProviderDTO saveProvider(ExternalLoginProviderSaveCmd c) {
        String code = StringUtils.hasText(c.getCode()) ? c.getCode().trim().toLowerCase() : "wwoyun";
        ExternalLoginProvider p = providerRepo.findByCode(code).orElseGet(() -> ExternalLoginProvider.builder().code(code).protocol("WWOYUN").build());
        p.update(c.getName(), c.getAppId(), c.getAppKey(), c.getCallbackUrl(), c.isEnabled(), c.getSupportedTypes());
        return toProvider(providerRepo.save(p));
    }

    @Transactional(readOnly = true)
    public List<ExternalLoginProviderDTO> providers() { return providerRepo.findAll().stream().map(this::toProvider).toList(); }

    @Transactional
    public ExternalLoginAuthorizationDTO authorize(String providerCode, String type, Long bindUserId) {
        ExternalLoginProvider p = active(providerCode);
        checkType(p, type);
        String state = token();
        ticketStore.saveState(state, new ExternalLoginTicketStore.State(providerCode, type, bindUserId));
        return ExternalLoginAuthorizationDTO.builder().state(state).authorizationUrl(gateway.authorizationUrl(p, type, state)).build();
    }

    @Transactional
    public ExternalLoginCallbackDTO callback(String providerCode, String type, String code, String state) {
        ExternalLoginTicketStore.State session = ticketStore.consumeState(state)
                .filter(s -> s.providerCode().equals(providerCode) && s.platformType().equals(type))
                .orElseThrow(() -> new BizException("第三方登录状态已失效或回调不匹配"));
        ExternalLoginProvider provider = active(providerCode);
        ExternalLoginGateway.ExternalIdentity identity = gateway.exchange(provider, type, code);
        ExternalLoginTicketStore.Binding binding = new ExternalLoginTicketStore.Binding(providerCode, type, identity.socialUid(),
                identity.nickname(), identity.avatarUrl(), identity.gender(), identity.location());
        ExternalAccount account = accountRepo.findByProviderAndPlatformAndSocialUid(providerCode, type, identity.socialUid()).orElse(null);
        if (session.bindUserId() != null) return bindOutcome(session.bindUserId(), binding, account);
        if (account == null) {
            String bindingToken = token();
            ticketStore.saveBinding(bindingToken, binding);
            return ExternalLoginCallbackDTO.builder().outcome(ExternalLoginCallbackDTO.Outcome.BIND_REQUIRED).bindingToken(bindingToken)
                    .providerCode(providerCode).type(type).nickname(identity.nickname()).avatarUrl(identity.avatarUrl()).build();
        }
        bindingAppService.ensureActiveUser(account.getUserId());
        account.refresh(identity.nickname(), identity.avatarUrl(), identity.gender(), identity.location());
        accountRepo.save(account);
        return ExternalLoginCallbackDTO.builder().outcome(ExternalLoginCallbackDTO.Outcome.LOGIN)
                .session(loginSession(account.getUserId())).build();
    }

    private UserLoginDTO loginSession(Long userId) {
        LoginTokenDTO token = loginTokenAppService.issueForLogin(userId);
        UserProfileDTO user = userAppService.profile(userId);
        return UserLoginDTO.builder()
                .token(token.getToken())
                .tokenName(token.getTokenName())
                .refreshToken(token.getRefreshToken())
                .dualTokenEnabled(token.isDualTokenEnabled())
                .expiresIn(token.getExpiresIn())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .avatar(user.getAvatar())
                .createTime(user.getCreateTime())
                .build();
    }

    private ExternalLoginCallbackDTO bindOutcome(Long ownerId, ExternalLoginTicketStore.Binding binding, ExternalAccount account) {
        if (account != null && !ownerId.equals(account.getUserId())) throw new BizException("该第三方账号已绑定其他用户");
        bindingAppService.ensureActiveUser(ownerId);
        bindingAppService.bind(binding, ownerId);
        return ExternalLoginCallbackDTO.builder().outcome(ExternalLoginCallbackDTO.Outcome.BOUND)
                .providerCode(binding.providerCode()).type(binding.platformType()).nickname(binding.nickname()).avatarUrl(binding.avatarUrl()).build();
    }

    @Transactional(readOnly = true)
    public List<ExternalAccountDTO> accounts(Long uid) { return accountRepo.findByUserId(uid).stream().map(this::toAccount).toList(); }

    @Transactional
    public void revoke(Long uid, Long id) { accountRepo.findByIdAndUserId(id, uid).orElseThrow(() -> new BizException("第三方账号不存在")); accountRepo.deleteById(id); }

    @Transactional
    public void useAvatar(Long uid, Long id) {
        ExternalAccount a = accountRepo.findByIdAndUserId(id, uid).orElseThrow(() -> new BizException("第三方账号不存在"));
        if (!StringUtils.hasText(a.getAvatarUrl())) throw new BizException("该第三方账号没有可用头像");
        try {
            java.net.URLConnection c = new java.net.URI(a.getAvatarUrl()).toURL().openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            String type = c.getContentType(); long size = c.getContentLengthLong();
            if (!StringUtils.hasText(type) || !type.toLowerCase().startsWith("image/")) throw new BizException("第三方头像不是图片");
            if (size > 10 * 1024 * 1024) throw new BizException("第三方头像文件过大");
            try (java.io.InputStream in = c.getInputStream()) { userAppService.updateAvatar(uid, in, "external-avatar", type, Math.max(size, 0)); }
        } catch (BizException e) { throw e; } catch (Exception e) { throw new BizException("下载第三方头像失败"); }
    }

    private ExternalLoginProvider active(String code) {
        ExternalLoginProvider p = providerRepo.findByCode(code).orElseThrow(() -> new BizException("第三方登录提供方不存在"));
        if (!p.isEnabled()) throw new BizException("第三方登录提供方未启用");
        if (!"WWOYUN".equals(p.getProtocol())) throw new BizException("暂不支持该第三方登录协议");
        return p;
    }
    private void checkType(ExternalLoginProvider p, String type) {
        if (!StringUtils.hasText(type) || Arrays.stream(p.getSupportedTypes().split(",")).map(String::trim).noneMatch(type::equalsIgnoreCase)) throw new BizException("该平台未启用");
    }
    private String token() { byte[] b = new byte[32]; random.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private ExternalLoginProviderDTO toProvider(ExternalLoginProvider p) { return ExternalLoginProviderDTO.builder().code(p.getCode()).name(p.getName()).protocol(p.getProtocol()).appId(p.getAppId()).callbackUrl(p.getCallbackUrl()).enabled(p.isEnabled()).supportedTypes(p.getSupportedTypes()).updateTime(p.getUpdateTime()).build(); }
    private ExternalAccountDTO toAccount(ExternalAccount a) { return ExternalAccountDTO.builder().id(a.getId()).providerCode(a.getProviderCode()).platformType(a.getPlatformType()).socialUid(a.getSocialUid()).nickname(a.getNickname()).avatarUrl(a.getAvatarUrl()).createTime(a.getCreateTime()).build(); }
}
