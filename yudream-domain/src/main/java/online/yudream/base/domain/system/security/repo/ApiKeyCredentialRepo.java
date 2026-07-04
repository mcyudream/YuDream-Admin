package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.ApiKeyCredential;

import java.util.List;
import java.util.Optional;

public interface ApiKeyCredentialRepo {

    ApiKeyCredential save(ApiKeyCredential credential);

    Optional<ApiKeyCredential> findById(Long id);

    Optional<ApiKeyCredential> findByPrefix(String prefix);

    List<ApiKeyCredential> page(String keyword, Long creatorUserId, int page, int size);

    long count(String keyword, Long creatorUserId);
}
