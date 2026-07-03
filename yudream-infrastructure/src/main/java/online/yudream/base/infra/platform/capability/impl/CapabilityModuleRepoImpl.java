package online.yudream.base.infra.platform.capability.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.capability.dataobj.CapabilityModuleDO;
import online.yudream.base.infra.platform.capability.mapper.CapabilityModuleInfraMapper;
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
public class CapabilityModuleRepoImpl implements CapabilityModuleRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public CapabilityModule save(CapabilityModule module) {
        CapabilityModuleDO dataObj = CapabilityModuleInfraMapper.toDataObj(module);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return CapabilityModuleInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<CapabilityModule> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(CapabilityModuleInfraMapper.toDomain(mongoTemplate.findOne(query, CapabilityModuleDO.class)));
    }

    @Override
    public List<CapabilityModule> findAll() {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "sort"));
        return mongoTemplate.find(query, CapabilityModuleDO.class).stream()
                .map(CapabilityModuleInfraMapper::toDomain)
                .toList();
    }
}
