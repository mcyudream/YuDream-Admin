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

    /** 删除该用户的全部外部登录绑定（账号删除级联，释放第三方身份）。 */
    void deleteByUserId(Long userId);
}
