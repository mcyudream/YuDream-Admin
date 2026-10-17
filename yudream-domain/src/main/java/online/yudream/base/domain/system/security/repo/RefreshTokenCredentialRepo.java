package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.RefreshTokenCredential;

import java.util.Optional;

public interface RefreshTokenCredentialRepo {

    RefreshTokenCredential save(RefreshTokenCredential credential);

    Optional<RefreshTokenCredential> findByTokenHash(String tokenHash);

    /** 删除该用户的全部刷新令牌（账号删除级联）。 */
    void deleteByUserId(Long userId);
}
