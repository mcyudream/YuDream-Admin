package online.yudream.base.infra.system.user.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.infra.system.user.dataobj.DeptDO;
import online.yudream.base.infra.system.user.mapper.DeptInfraMapper;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 部门仓库实现。
 */
@Service
@RequiredArgsConstructor
public class DeptRepoImpl implements DeptRepo {

    private final MongoTemplate mongoTemplate;

    @Override
    public Dept save(Dept dept) {
        DeptDO deptDO = DeptInfraMapper.toDataObj(dept);
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
}
