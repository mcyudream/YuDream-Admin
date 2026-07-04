package online.yudream.base.infra.platform.plugin.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.plugin.dataobj.PluginModuleDO;
import online.yudream.base.infra.platform.plugin.mapper.PluginModuleInfraMapper;
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
public class PluginModuleRepoImpl implements PluginModuleRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public PluginModule save(PluginModule module) {
        PluginModuleDO dataObj = PluginModuleInfraMapper.toDataObj(module);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return PluginModuleInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<PluginModule> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(PluginModuleInfraMapper.toDomain(mongoTemplate.findOne(query, PluginModuleDO.class)));
    }

    @Override
    public List<PluginModule> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "code"));
        return mongoTemplate.find(query, PluginModuleDO.class).stream()
                .map(PluginModuleInfraMapper::toDomain)
                .toList();
    }
}
