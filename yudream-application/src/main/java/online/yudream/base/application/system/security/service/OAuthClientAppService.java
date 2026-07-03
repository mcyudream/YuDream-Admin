package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.cmd.OAuthClientAuthorizeCmd;
import online.yudream.base.application.system.security.cmd.OAuthClientCallbackCmd;
import online.yudream.base.application.system.security.dto.OAuthClientAuthorizeDTO;
import online.yudream.base.application.system.security.dto.OAuthClientCallbackDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;
import online.yudream.base.domain.system.security.enumerate.OAuthRegistrationStatus;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.OAuthProviderRegistrationRepo;
import online.yudream.base.domain.system.security.service.OAuthClientGateway;
import online.yudream.base.domain.system.security.valobj.OAuthClientToken;
import online.yudream.base.domain.system.security.valobj.OAuthClientUserInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OAuthClientAppService {

    private static final int STATE_BYTES = 16;

    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final OAuthProviderRegistrationRepo oauthProviderRegistrationRepo;
    private final OAuthClientGateway oauthClientGateway;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional(readOnly = true)
    public OAuthClientAuthorizeDTO authorize(OAuthClientAuthorizeCmd cmd) {
        ensureClientEnabled();
        OAuthProviderRegistration provider = activeProvider(cmd.getProviderCode());
        String state = StringUtils.hasText(cmd.getState()) ? cmd.getState() : randomState();
        String authorizationUrl = authorizationUrl(provider, state);
        return OAuthClientAuthorizeDTO.builder()
                .providerCode(provider.getCode())
                .authorizationUrl(authorizationUrl)
                .state(state)
                .build();
    }

    @Transactional(readOnly = true)
    public OAuthClientCallbackDTO callback(OAuthClientCallbackCmd cmd) {
        ensureClientEnabled();
        OAuthProviderRegistration provider = activeProvider(cmd.getProviderCode());
        if (!StringUtils.hasText(cmd.getCode())) {
            throw new BizException("OAuth 回调 code 不能为空");
        }
        OAuthClientToken token = oauthClientGateway.exchangeCode(provider, cmd.getCode(), cmd.getState());
        OAuthClientUserInfo userInfo = oauthClientGateway.userInfo(provider, token);
        return OAuthClientCallbackDTO.builder()
                .providerCode(provider.getCode())
                .subject(userInfo.subject())
                .username(userInfo.username())
                .nickname(userInfo.nickname())
                .email(userInfo.email())
                .avatar(userInfo.avatar())
                .raw(userInfo.raw())
                .build();
    }

    private void ensureClientEnabled() {
        ApiSecurityPolicy policy = apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
        if (!policy.isOauthClientEnabled()) {
            throw new BizException("OAuth 客户端未启用");
        }
    }

    private OAuthProviderRegistration activeProvider(String code) {
        OAuthProviderRegistration provider = oauthProviderRegistrationRepo.findByCode(code)
                .orElseThrow(() -> new BizException("OAuth 提供商不存在"));
        if (provider.getStatus() != OAuthRegistrationStatus.ACTIVE) {
            throw new BizException("OAuth 提供商已停用");
        }
        return provider;
    }

    private String authorizationUrl(OAuthProviderRegistration provider, String state) {
        if (!StringUtils.hasText(provider.getAuthorizationUri())
                || !StringUtils.hasText(provider.getClientId())
                || !StringUtils.hasText(provider.getRedirectUri())) {
            throw new BizException("OAuth 提供商授权配置不完整");
        }
        StringBuilder builder = new StringBuilder(provider.getAuthorizationUri());
        builder.append(provider.getAuthorizationUri().contains("?") ? "&" : "?")
                .append("response_type=code")
                .append("&client_id=").append(encode(provider.getClientId()))
                .append("&redirect_uri=").append(encode(provider.getRedirectUri()))
                .append("&scope=").append(encode(scope(provider.getScopes())))
                .append("&state=").append(encode(state));
        return builder.toString();
    }

    private String scope(List<String> scopes) {
        return scopes == null || scopes.isEmpty() ? "openid profile email" : String.join(" ", scopes);
    }

    private String randomState() {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return "yds_" + HexFormat.of().formatHex(bytes);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
