package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ExternalAccount;
import online.yudream.base.domain.system.security.repo.ExternalAccountRepo;
import online.yudream.base.domain.system.security.service.ExternalLoginTicketStore;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ExternalLoginBindingAppService {
    private final ExternalLoginTicketStore ticketStore;
    private final ExternalAccountRepo accountRepo;
    private final UserRepo userRepo;

    @Transactional
    public void claim(String bindingToken, Long userId) {
        if (!StringUtils.hasText(bindingToken)) return;
        ExternalLoginTicketStore.Binding binding = ticketStore.consumeBinding(bindingToken)
                .orElseThrow(() -> new BizException("第三方账号绑定凭证已失效"));
        ensureActiveUser(userId);
        bind(binding, userId);
    }

    @Transactional(readOnly = true)
    public void ensureActiveUser(Long userId) {
        if (userId == null) throw new BizException("用户不存在");
        var user = userRepo.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        if (user.getStatus() == UserStatus.DISABLED) throw new BizException("用户已停用");
    }

    @Transactional
    public void bind(ExternalLoginTicketStore.Binding binding, Long userId) {
        ExternalAccount existing = accountRepo.findByProviderAndPlatformAndSocialUid(
                binding.providerCode(), binding.platformType(), binding.socialUid()).orElse(null);
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) throw new BizException("该第三方账号已绑定其他用户");
            existing.refresh(binding.nickname(), binding.avatarUrl(), binding.gender(), binding.location());
            accountRepo.save(existing);
            return;
        }
        accountRepo.save(ExternalAccount.builder().userId(userId).providerCode(binding.providerCode())
                .platformType(binding.platformType()).socialUid(binding.socialUid()).nickname(binding.nickname())
                .avatarUrl(binding.avatarUrl()).gender(binding.gender()).location(binding.location()).build());
    }
}
