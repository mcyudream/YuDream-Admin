package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.PasskeyCredential;

import java.util.List;
import java.util.Optional;

public interface PasskeyCredentialRepo {

    PasskeyCredential save(PasskeyCredential credential);

    Optional<PasskeyCredential> findById(Long id);

    Optional<PasskeyCredential> findByCredentialId(String credentialId);

    List<PasskeyCredential> findAll();

    List<PasskeyCredential> findByUserId(Long userId);
}
