package online.yudream.base.infra.system.backup.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.repo.RemoteTargetRepo;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import online.yudream.base.infra.system.backup.dataobj.RemoteTargetDO;
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
public class RemoteTargetRepoImpl implements RemoteTargetRepo {

    private final MongoTemplate mongo;
    private final IdGenerator ids;
    private final CapabilityCredentialCipher cipher;

    @Override
    public RemoteTarget save(RemoteTarget target) {
        RemoteTargetDO dataObj = BackupInfraMapper.target(target, cipher);
        if (dataObj.getId() == null) {
            dataObj.setId(ids.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        try {
            dataObj = mongo.save(dataObj);
        } catch (DuplicateKeyException e) {
            throw new BizException("目标编码已存在：" + target.getCode());
        }
        target.setVersion(dataObj.getVersion());
        return BackupInfraMapper.target(dataObj, cipher);
    }

    @Override
    public Optional<RemoteTarget> findById(Long id) {
        return Optional.ofNullable(BackupInfraMapper.target(mongo.findById(id, RemoteTargetDO.class), cipher));
    }

    @Override
    public Optional<RemoteTarget> findByCode(String code) {
        return Optional.ofNullable(BackupInfraMapper.target(
                mongo.findOne(Query.query(Criteria.where("code").is(code)), RemoteTargetDO.class), cipher));
    }

    @Override
    public List<RemoteTarget> findAll() {
        return mongo.find(Query.query(new Criteria()).with(Sort.by(Sort.Direction.ASC, "code")), RemoteTargetDO.class)
                .stream().map(dataObj -> BackupInfraMapper.target(dataObj, cipher)).toList();
    }

    @Override
    public List<RemoteTarget> findByEnabled(boolean enabled) {
        return mongo.find(Query.query(Criteria.where("enabled").is(enabled)), RemoteTargetDO.class)
                .stream().map(dataObj -> BackupInfraMapper.target(dataObj, cipher)).toList();
    }

    @Override
    public void deleteById(Long id) {
        mongo.remove(Query.query(Criteria.where("id").is(id)), RemoteTargetDO.class);
    }
}
