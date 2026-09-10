package online.yudream.base.infra.platform.cms.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.cms.aggregate.HomePagePreset;
import online.yudream.base.domain.platform.cms.enumerate.HomePagePresetSource;
import online.yudream.base.domain.platform.cms.repo.HomePagePresetRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.cms.dataobj.HomePagePresetDO;
import online.yudream.base.infra.platform.cms.mapper.CmsInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HomePagePresetRepoImpl implements HomePagePresetRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public HomePagePreset save(HomePagePreset preset) {
        HomePagePresetDO dataObj = CmsInfraMapper.toDataObj(preset);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return CmsInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<HomePagePreset> findByCode(String code) {
        Query query = new Query(Criteria.where("code").is(code)).limit(1);
        return Optional.ofNullable(CmsInfraMapper.toDomain(mongoTemplate.findOne(query, HomePagePresetDO.class)));
    }

    @Override
    public List<HomePagePreset> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, HomePagePresetDO.class).stream()
                .map(CmsInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<HomePagePreset> findByThemeCode(String themeCode) {
        Query query = new Query(Criteria.where("themeCode").is(themeCode))
                .with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, HomePagePresetDO.class).stream()
                .map(CmsInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<HomePagePreset> findBySource(HomePagePresetSource source) {
        Query query = new Query(Criteria.where("source").is(source))
                .with(Sort.by(Sort.Direction.DESC, "createTime"));
        return mongoTemplate.find(query, HomePagePresetDO.class).stream()
                .map(CmsInfraMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteByCode(String code) {
        mongoTemplate.remove(new Query(Criteria.where("code").is(code)), HomePagePresetDO.class);
    }
}
