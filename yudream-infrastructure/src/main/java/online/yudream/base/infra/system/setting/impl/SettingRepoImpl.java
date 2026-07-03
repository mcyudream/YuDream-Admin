package online.yudream.base.infra.system.setting.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.infra.system.setting.dataobj.SettingDO;
import online.yudream.base.infra.system.setting.mapper.SettingInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 系统设置仓库实现。
 */
@Service
@RequiredArgsConstructor
public class SettingRepoImpl implements SettingRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public Setting save(Setting setting) {
        SettingDO settingDO = SettingInfraMapper.toDataObj(setting);
        if (settingDO.getId() == null) {
            settingDO.setId(idGenerator.nextId());
            settingDO.setCreateTime(LocalDateTime.now());
        }
        settingDO.setUpdateTime(LocalDateTime.now());
        SettingDO saved = mongoTemplate.save(settingDO);
        return SettingInfraMapper.toDomain(saved);
    }

    @Override
    public Optional<Setting> findByKey(String key) {
        Query query = Query.query(Criteria.where("key").is(key));
        SettingDO settingDO = mongoTemplate.findOne(query, SettingDO.class);
        return Optional.ofNullable(SettingInfraMapper.toDomain(settingDO));
    }

    @Override
    public boolean existsByKey(String key) {
        Query query = Query.query(Criteria.where("key").is(key));
        return mongoTemplate.exists(query, SettingDO.class);
    }

    @Override
    public List<Setting> findByCategory(String category) {
        Query query = Query.query(Criteria.where("category").is(category));
        List<SettingDO> list = mongoTemplate.find(query, SettingDO.class);
        return list.stream().map(SettingInfraMapper::toDomain).toList();
    }

    @Override
    public List<Setting> findAll() {
        List<SettingDO> list = mongoTemplate.findAll(SettingDO.class);
        return list.stream().map(SettingInfraMapper::toDomain).toList();
    }
}
