package online.yudream.base.domain.system.security.repo;

import online.yudream.base.domain.system.security.aggregate.ApiSecurityPolicy;

import java.util.Optional;

public interface ApiSecurityPolicyRepo {

    ApiSecurityPolicy save(ApiSecurityPolicy policy);

    Optional<ApiSecurityPolicy> findDefault();
}
