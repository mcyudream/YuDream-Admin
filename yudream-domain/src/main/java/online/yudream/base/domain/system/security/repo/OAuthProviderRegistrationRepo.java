package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.OAuthProviderRegistration;

import java.util.List;
import java.util.Optional;

public interface OAuthProviderRegistrationRepo {

    OAuthProviderRegistration save(OAuthProviderRegistration registration);

    Optional<OAuthProviderRegistration> findById(Long id);

    Optional<OAuthProviderRegistration> findByCode(String code);

    List<OAuthProviderRegistration> findAll();
}
