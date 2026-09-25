package online.yudream.base.infra.system.backup.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.repo.BackupPlanRepo;
import online.yudream.base.infra.system.backup.dataobj.BackupPlanDO;
import online.yudream.base.infra.system.backup.mapper.BackupInfraMapper;
import org.springframework.dao.DuplicateKeyException;
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
public class BackupPlanRepoImpl implements BackupPlanRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public BackupPlan save(BackupPlan plan) {
        BackupPlanDO dataObj = BackupInfraMapper.plan(plan);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        try {
            dataObj = mongo.save(dataObj);
        } catch (DuplicateKeyException e) {
            throw new BizException("计划编码已存在：" + plan.getCode());
        }
        plan.setVersion(dataObj.getVersion());
        return BackupInfraMapper.plan(dataObj);
    }

    @Override
    public Optional<BackupPlan> findById(Long id) {
        return Optional.ofNullable(BackupInfraMapper.plan(mongo.findById(id, BackupPlanDO.class)));
    }

    @Override
    public Optional<BackupPlan> findByCode(String code) {
        return Optional.ofNullable(BackupInfraMapper.plan(
                mongo.findOne(Query.query(Criteria.where("code").is(code)), BackupPlanDO.class)));
    }

    @Override
    public List<BackupPlan> findAll() {
        return mongo.find(Query.query(new Criteria()).with(Sort.by(Sort.Direction.ASC, "code")), BackupPlanDO.class)
                .stream().map(BackupInfraMapper::plan).toList();
    }

    @Override
    public List<BackupPlan> findByEnabled(boolean enabled) {
        return mongo.find(Query.query(Criteria.where("enabled").is(enabled)), BackupPlanDO.class)
                .stream().map(BackupInfraMapper::plan).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), BackupPlanDO.class);
    }
}
