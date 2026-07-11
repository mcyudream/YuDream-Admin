package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.ExternalLoginProvider;
import java.util.List;
import java.util.Optional;

public interface ExternalLoginProviderRepo {
    ExternalLoginProvider save(ExternalLoginProvider provider);
    Optional<ExternalLoginProvider> findByCode(String code);
    List<ExternalLoginProvider> findAll();
}
