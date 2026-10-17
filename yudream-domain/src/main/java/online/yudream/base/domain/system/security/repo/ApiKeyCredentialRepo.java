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

    /** 删除该用户创建的全部 API Key（账号删除级联，避免凭据继续可认证）。 */
    void deleteByUserId(Long userId);
}
