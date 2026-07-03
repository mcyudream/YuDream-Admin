package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.OAuthClientRegistration;

import java.util.List;
import java.util.Optional;

public interface OAuthClientRegistrationRepo {

    OAuthClientRegistration save(OAuthClientRegistration registration);

    Optional<OAuthClientRegistration> findById(Long id);

    Optional<OAuthClientRegistration> findByClientId(String clientId);

    List<OAuthClientRegistration> findAll();
}
