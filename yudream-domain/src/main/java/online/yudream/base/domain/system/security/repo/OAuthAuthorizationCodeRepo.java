package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.OAuthAuthorizationCode;

import java.util.Optional;

public interface OAuthAuthorizationCodeRepo {

    OAuthAuthorizationCode save(OAuthAuthorizationCode authorizationCode);

    Optional<OAuthAuthorizationCode> findByCode(String code);

    /** 删除该用户的全部授权码（账号删除级联）。 */
    void deleteByUserId(Long userId);
}
