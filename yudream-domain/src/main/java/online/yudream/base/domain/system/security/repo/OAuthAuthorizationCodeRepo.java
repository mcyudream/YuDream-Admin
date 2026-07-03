package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.OAuthAuthorizationCode;

import java.util.Optional;

public interface OAuthAuthorizationCodeRepo {

    OAuthAuthorizationCode save(OAuthAuthorizationCode authorizationCode);

    Optional<OAuthAuthorizationCode> findByCode(String code);
}
