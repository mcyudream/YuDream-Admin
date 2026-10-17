package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.OAuthAccessToken;

import java.util.Optional;

public interface OAuthAccessTokenRepo {

    OAuthAccessToken save(OAuthAccessToken token);

    Optional<OAuthAccessToken> findByRefreshTokenHash(String refreshTokenHash);

    /**
     * 按 access token 哈希查找。{@code accessTokenHash} 应为 SHA-256(plaintext)。
     * 用于请求上下文（如 SecurityPrincipalSupport、PluginDispatchController）按 Bearer 解析当前用户。
     */
    Optional<OAuthAccessToken> findByAccessTokenHash(String accessTokenHash);

    /** 删除该用户的全部访问令牌（账号删除级联）。 */
    void deleteByUserId(Long userId);
}
