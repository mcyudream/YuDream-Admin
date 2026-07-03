package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.cmd.LoginTokenRefreshCmd;
import online.yudream.base.application.system.security.dto.LoginTokenDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;
import online.yudream.base.domain.system.security.aggregate.RefreshTokenCredential;
import online.yudream.base.domain.system.security.repo.ApiSecurityPolicyRepo;
import online.yudream.base.domain.system.security.repo.RefreshTokenCredentialRepo;
import online.yudream.base.domain.system.security.service.LoginTokenGateway;
import online.yudream.base.domain.system.security.valobj.TokenPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class LoginTokenAppService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final ApiSecurityPolicyRepo apiSecurityPolicyRepo;
    private final RefreshTokenCredentialRepo refreshTokenCredentialRepo;
    private final LoginTokenGateway loginTokenGateway;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public LoginTokenDTO issueForLogin(Long userId) {
        ApiSecurityPolicy policy = currentPolicy();
        TokenPolicy tokenPolicy = tokenPolicy(policy);
        String accessToken = loginTokenGateway.issueAccessToken(
                userId,
                policy.isDualTokenEnabled() ? tokenPolicy.accessTokenTtlSeconds() : null
        );
        String refreshToken = policy.isDualTokenEnabled()
                ? issueRefreshToken(userId, tokenPolicy.refreshTokenTtlSeconds())
                : null;
        return LoginTokenDTO.builder()
                .token(accessToken)
                .tokenName(loginTokenGateway.tokenName())
                .refreshToken(refreshToken)
                .dualTokenEnabled(policy.isDualTokenEnabled())
                .expiresIn(policy.isDualTokenEnabled() ? tokenPolicy.accessTokenTtlSeconds() : 0)
                .build();
    }

    @Transactional
    public LoginTokenDTO refresh(LoginTokenRefreshCmd cmd) {
        ApiSecurityPolicy policy = currentPolicy();
        if (!policy.isDualTokenEnabled()) {
            throw new BizException("双 Token 未启用");
        }
        if (cmd == null || !StringUtils.hasText(cmd.getRefreshToken())) {
            throw new BizException("刷新令牌不能为空");
        }
        TokenPolicy tokenPolicy = tokenPolicy(policy);
        RefreshTokenCredential credential = refreshTokenCredentialRepo.findByTokenHash(ApiKeySecretHasher.hash(cmd.getRefreshToken()))
                .orElseThrow(() -> new BizException("刷新令牌无效或已过期"));
        credential.markUsed(LocalDateTime.now());
        String nextRefreshToken = cmd.getRefreshToken();
        if (tokenPolicy.refreshRotationEnabled()) {
            credential.revoke();
            nextRefreshToken = issueRefreshToken(credential.getUserId(), tokenPolicy.refreshTokenTtlSeconds());
        }
        refreshTokenCredentialRepo.save(credential);
        String accessToken = loginTokenGateway.issueAccessToken(credential.getUserId(), tokenPolicy.accessTokenTtlSeconds());
        return LoginTokenDTO.builder()
                .token(accessToken)
                .tokenName(loginTokenGateway.tokenName())
                .refreshToken(nextRefreshToken)
                .dualTokenEnabled(true)
                .expiresIn(tokenPolicy.accessTokenTtlSeconds())
                .build();
    }

    private String issueRefreshToken(Long userId, long ttlSeconds) {
        String plaintext = randomRefreshToken();
        RefreshTokenCredential credential = RefreshTokenCredential.issue(
                ApiKeySecretHasher.hash(plaintext),
                userId,
                LocalDateTime.now().plusSeconds(ttlSeconds)
        );
        refreshTokenCredentialRepo.save(credential);
        return plaintext;
    }

    private ApiSecurityPolicy currentPolicy() {
        return apiSecurityPolicyRepo.findDefault().orElse(ApiSecurityPolicy.createDefault());
    }

    private TokenPolicy tokenPolicy(ApiSecurityPolicy policy) {
        return policy.getTokenPolicy() == null ? TokenPolicy.defaultPolicy() : policy.getTokenPolicy();
    }

    private String randomRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return "ydr_" + HexFormat.of().formatHex(bytes);
    }
}
