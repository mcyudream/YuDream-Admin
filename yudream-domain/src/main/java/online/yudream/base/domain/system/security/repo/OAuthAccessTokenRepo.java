package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.OAuthAccessToken;

import java.util.Optional;

public interface OAuthAccessTokenRepo {

    OAuthAccessToken save(OAuthAccessToken token);

    Optional<OAuthAccessToken> findByRefreshTokenHash(String refreshTokenHash);
}
