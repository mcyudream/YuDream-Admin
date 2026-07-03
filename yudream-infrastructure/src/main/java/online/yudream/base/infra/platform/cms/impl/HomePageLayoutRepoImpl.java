package online.yudream.base.infra.platform.cms.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.cms.dataobj.HomePageLayoutDO;
import online.yudream.base.infra.platform.cms.mapper.CmsInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HomePageLayoutRepoImpl implements HomePageLayoutRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public HomePageLayout save(HomePageLayout layout) {
        HomePageLayoutDO dataObj = CmsInfraMapper.toDataObj(layout);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return CmsInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<HomePageLayout> findCurrent() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "updateTime")).limit(1);
        return Optional.ofNullable(CmsInfraMapper.toDomain(mongoTemplate.findOne(query, HomePageLayoutDO.class)));
    }
}
