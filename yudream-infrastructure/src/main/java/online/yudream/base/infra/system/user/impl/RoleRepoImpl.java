package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.infra.system.user.dataobj.RoleDO;
import online.yudream.base.infra.system.user.mapper.RoleInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 角色仓库实现。
 */
@Service
@RequiredArgsConstructor
public class RoleRepoImpl implements RoleRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public Role save(Role role) {
        RoleDO roleDO = RoleInfraMapper.toDataObj(role);
        RoleDO saved = mongoTemplate.save(roleDO);
        return RoleInfraMapper.toDomain(saved);
    }

    @Override
    public Optional<Role> findById(Long id) {
        RoleDO roleDO = mongoTemplate.findById(id, RoleDO.class);
        return Optional.ofNullable(RoleInfraMapper.toDomain(roleDO));
    }

    @Override
    public Optional<Role> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        RoleDO roleDO = mongoTemplate.findOne(query, RoleDO.class);
        return Optional.ofNullable(RoleInfraMapper.toDomain(roleDO));
    }

    @Override
    public Optional<Role> findBySystemType(SystemRoleType systemType) {
        Query query = Query.query(Criteria.where("systemType").is(systemType));
        RoleDO roleDO = mongoTemplate.findOne(query, RoleDO.class);
        return Optional.ofNullable(RoleInfraMapper.toDomain(roleDO));
    }
}
