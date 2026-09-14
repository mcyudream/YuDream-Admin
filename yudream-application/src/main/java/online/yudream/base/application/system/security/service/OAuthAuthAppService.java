package online.yudream.base.application.system.security.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.aggregate.OAuthAccessToken;
import online.yudream.base.domain.system.security.enumerate.CredentialStatus;
import online.yudream.base.domain.system.security.repo.OAuthAccessTokenRepo;
import online.yudream.base.domain.system.security.valobj.OAuthAuthentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth 访问令牌的请求级鉴权服务。
 * <p>
 * 与 {@link ApiKeyAuthAppService} 平级，但行为不同：
 * <ul>
 *   <li>按 SHA-256 哈希定位 OAuthAccessToken（不消耗、不写最后使用时间）；</li>
 *   <li>校验 ACTIVE + 未过期；</li>
 *   <li>权限列表直接取自 OAuth scopes（scope 与权限码同形）。</li>
 * </ul>
 * 返回的 {@link OAuthAuthentication} 由 {@link online.yudream.base.domain.system.security.service.OAuthAuthenticationContext}
 * 写入当前线程，供 SecurityPrincipalSupport 读取。
 */
@Service
@RequiredArgsConstructor
public class OAuthAuthAppService {

    private final OAuthAccessTokenRepo oauthAccessTokenRepo;

    public OAuthAuthentication authenticate(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new BizException("OAuth 访问令牌无效");
        }
        OAuthAccessToken token = oauthAccessTokenRepo.findByAccessTokenHash(ApiKeySecretHasher.hash(plaintext))
                .orElseThrow(() -> new BizException("OAuth 访问令牌无效或已过期"));
        if (token.getStatus() != CredentialStatus.ACTIVE) {
            throw new BizException("OAuth 访问令牌已失效");
        }
        LocalDateTime now = LocalDateTime.now();
        if (token.getAccessExpireTime() == null || !token.getAccessExpireTime().isAfter(now)) {
            throw new BizException("OAuth 访问令牌已过期");
        }
        List<String> scopes = token.getScopes() == null ? List.of() : List.copyOf(token.getScopes());
        String tokenId = token.getId() == null ? null : String.valueOf(token.getId());
        return new OAuthAuthentication(tokenId, token.getUserId(), token.getClientId(), scopes);
    }
}