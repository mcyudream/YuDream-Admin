package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.RefreshTokenCredential;

import java.util.Optional;

public interface RefreshTokenCredentialRepo {

    RefreshTokenCredential save(RefreshTokenCredential credential);

    Optional<RefreshTokenCredential> findByTokenHash(String tokenHash);
}
