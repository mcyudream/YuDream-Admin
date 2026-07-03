package online.yudream.base.domain.platform.cms.repo;

import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;

import java.util.Optional;

public interface HomePageLayoutRepo {

    HomePageLayout save(HomePageLayout layout);

    Optional<HomePageLayout> findCurrent();
}
