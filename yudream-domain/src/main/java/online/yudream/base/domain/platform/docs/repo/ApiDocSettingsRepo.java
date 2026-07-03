package online.yudream.base.domain.platform.docs.repo;

import online.yudream.base.domain.platform.docs.aggregate.ApiDocSettings;

import java.util.Optional;

public interface ApiDocSettingsRepo {

    ApiDocSettings save(ApiDocSettings settings);

    Optional<ApiDocSettings> findDefault();
}
