package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.infra.system.user.dataobj.RoleDO;
import online.yudream.base.infra.system.user.mapper.RoleInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 角色仓库实现。
 */
@Service
@RequiredArgsConstructor
public class RoleRepoImpl implements RoleRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public Role save(Role role) {
        RoleDO roleDO = RoleInfraMapper.toDataObj(role);
        if (roleDO.getId() == null) {
            roleDO.setId(idGenerator.nextId());
            roleDO.setCreateTime(LocalDateTime.now());
        }
        roleDO.setUpdateTime(LocalDateTime.now());
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

    @Override
    public List<Role> findAll() {
        return mongoTemplate.findAll(RoleDO.class).stream()
                .map(RoleInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<Role> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), RoleDO.class).stream()
                .map(RoleInfraMapper::toDomain)
                .toList();
    }

    @Override
    public PageResult<Role> page(String keyword, Long deptId, RoleStatus status, int page, int size) {
        Query query = buildPageQuery(keyword, deptId, status);
        long total = mongoTemplate.count(query, RoleDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        List<Role> records = mongoTemplate.find(query, RoleDO.class).stream()
                .map(RoleInfraMapper::toDomain)
                .toList();
        return new PageResult<>(records, total, currentPage, pageSize);
    }

    @Override
    public boolean existsByCodeExcludeId(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        Query query = Query.query(Criteria.where("code").is(code));
        if (excludeId != null) {
            query.addCriteria(Criteria.where("id").ne(excludeId));
        }
        return mongoTemplate.exists(query, RoleDO.class);
    }

    private Query buildPageQuery(String keyword, Long deptId, RoleStatus status) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("code").regex(pattern, "i")
            ));
        }
        if (deptId != null) {
            query.addCriteria(Criteria.where("deptId").is(deptId));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        return query;
    }
}
