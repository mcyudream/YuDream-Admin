package online.yudream.base.infra.system.backup.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.enumerate.BackupJobStatus;
import online.yudream.base.domain.system.backup.repo.BackupJobRepo;
import online.yudream.base.infra.system.backup.dataobj.BackupJobDO;
import online.yudream.base.infra.system.backup.mapper.BackupInfraMapper;
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
public class BackupJobRepoImpl implements BackupJobRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;

    @Override
    public BackupJob save(BackupJob job) {
        BackupJobDO dataObj = BackupInfraMapper.job(job);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        BackupJobDO saved = mongo.save(dataObj);
        job.setVersion(saved.getVersion());
        return BackupInfraMapper.job(saved);
    }

    @Override
    public Optional<BackupJob> findById(Long id) {
        return Optional.ofNullable(BackupInfraMapper.job(mongo.findById(id, BackupJobDO.class)));
    }

    @Override
    public Optional<BackupJob> findNextQueued() {
        return Optional.ofNullable(BackupInfraMapper.job(mongo.findOne(
                Query.query(Criteria.where("status").is(BackupJobStatus.QUEUED))
                        .with(Sort.by(Sort.Direction.ASC, "createTime", "id")),
                BackupJobDO.class)));
    }

    @Override
    public List<BackupJob> findByStatus(BackupJobStatus status) {
        return mongo.find(Query.query(Criteria.where("status").is(status)), BackupJobDO.class)
                .stream().map(BackupInfraMapper::job).toList();
    }

    @Override
    public List<BackupJob> findRecent(int limit) {
        return mongo.find(Query.query(new Criteria()).with(Sort.by(Sort.Direction.DESC, "createTime", "id"))
                        .limit(limit), BackupJobDO.class)
                .stream().map(BackupInfraMapper::job).toList();
    }

    @Override
    public List<BackupJob> findByPlanCodeAndStatus(String planCode, BackupJobStatus status) {
        return mongo.find(Query.query(Criteria.where("planCode").is(planCode).and("status").is(status)),
                BackupJobDO.class).stream().map(BackupInfraMapper::job).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), BackupJobDO.class);
    }
}
