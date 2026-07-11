package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.ExternalAccount;
import java.util.List;
import java.util.Optional;

public interface ExternalAccountRepo {
    ExternalAccount save(ExternalAccount account);
    Optional<ExternalAccount> findByProviderAndPlatformAndSocialUid(String providerCode, String platformType, String socialUid);
    List<ExternalAccount> findByUserId(Long userId);
    Optional<ExternalAccount> findByIdAndUserId(Long id, Long userId);
    void deleteById(Long id);
}
