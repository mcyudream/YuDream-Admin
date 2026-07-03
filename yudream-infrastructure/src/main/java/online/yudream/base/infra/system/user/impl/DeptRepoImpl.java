package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.infra.system.user.dataobj.DeptDO;
import online.yudream.base.infra.system.user.mapper.DeptInfraMapper;
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
 * 部门仓库实现。
 */
@Service
@RequiredArgsConstructor
public class DeptRepoImpl implements DeptRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public Dept save(Dept dept) {
        DeptDO deptDO = DeptInfraMapper.toDataObj(dept);
        if (deptDO.getId() == null) {
            deptDO.setId(idGenerator.nextId());
            deptDO.setCreateTime(LocalDateTime.now());
        }
        deptDO.setUpdateTime(LocalDateTime.now());
        DeptDO saved = mongoTemplate.save(deptDO);
        return DeptInfraMapper.toDomain(saved);
    }

    @Override
    public Optional<Dept> findById(Long id) {
        DeptDO deptDO = mongoTemplate.findById(id, DeptDO.class);
        return Optional.ofNullable(DeptInfraMapper.toDomain(deptDO));
    }

    @Override
    public Optional<Dept> findRoot() {
        Query query = Query.query(Criteria.where("deptType").is(SystemDeptType.ROOT));
        DeptDO deptDO = mongoTemplate.findOne(query, DeptDO.class);
        return Optional.ofNullable(DeptInfraMapper.toDomain(deptDO));
    }

    @Override
    public Optional<Dept> findByType(SystemDeptType type) {
        Query query = Query.query(Criteria.where("deptType").is(type));
        DeptDO deptDO = mongoTemplate.findOne(query, DeptDO.class);
        return Optional.ofNullable(DeptInfraMapper.toDomain(deptDO));
    }

    @Override
    public List<Dept> findAll() {
        return mongoTemplate.findAll(DeptDO.class).stream()
                .map(DeptInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<Dept> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return mongoTemplate.find(Query.query(Criteria.where("id").in(ids)), DeptDO.class).stream()
                .map(DeptInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<Dept> findChildren(Long parentId) {
        Query query = parentId == null
                ? Query.query(Criteria.where("parentId").is(null))
                : Query.query(Criteria.where("parentId").is(parentId));
        return mongoTemplate.find(query, DeptDO.class).stream()
                .map(DeptInfraMapper::toDomain)
                .toList();
    }

    @Override
    public List<Dept> tree(String keyword, Long parentId, DeptStatus status) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")
            ));
        }
        if (parentId != null) {
            query.addCriteria(Criteria.where("parentId").is(parentId));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        return mongoTemplate.find(query, DeptDO.class).stream()
                .map(DeptInfraMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByNameAndParentExcludeId(String name, Long parentId, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        Query query = Query.query(Criteria.where("name").is(name));
        query.addCriteria(parentId == null ? Criteria.where("parentId").is(null) : Criteria.where("parentId").is(parentId));
        if (excludeId != null) {
            query.addCriteria(Criteria.where("id").ne(excludeId));
        }
        return mongoTemplate.exists(query, DeptDO.class);
    }

    @Override
    public long countActiveChildren(Long parentId) {
        if (parentId == null) {
            return 0;
        }
        Query query = Query.query(Criteria.where("parentId").is(parentId).and("status").is(DeptStatus.ACTIVE));
        return mongoTemplate.count(query, DeptDO.class);
    }
}
