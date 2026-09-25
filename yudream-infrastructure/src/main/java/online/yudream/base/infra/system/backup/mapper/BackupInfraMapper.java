package online.yudream.base.infra.system.backup.mapper;

import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.infra.system.backup.dataobj.BackupJobDO;
import online.yudream.base.infra.system.backup.dataobj.BackupPlanDO;
import online.yudream.base.infra.system.backup.dataobj.RemoteTargetDO;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.springframework.util.StringUtils;

import java.util.Optional;

/** 备份域领域对象与数据对象映射；目标密码经主密钥加解密（AAD 绑定 target:{code}）。 */
public final class BackupInfraMapper {

    /** 凭据加密的能力码命名空间。 */
    public static final String CIPHER_CODE = "system-backup";

    private BackupInfraMapper() {
    }

    public static BackupJobDO job(BackupJob job) {
        BackupJobDO dataObj = new BackupJobDO();
        dataObj.setId(job.getId());
        dataObj.setVersion(job.getVersion());
        dataObj.setCreateTime(job.getCreateTime());
        dataObj.setUpdateTime(job.getUpdateTime());
        dataObj.setType(job.getType());
        dataObj.setStatus(job.getStatus());
        dataObj.setTrigger(job.getTrigger());
        dataObj.setScopeTags(job.getScopeTags());
        dataObj.setPlanCode(job.getPlanCode());
        dataObj.setTargetCode(job.getTargetCode());
        dataObj.setTargetName(job.getTargetName());
        dataObj.setStrategy(job.getStrategy());
        dataObj.setArchiveName(job.getArchiveName());
        dataObj.setArchivePath(job.getArchivePath());
        dataObj.setArchiveSize(job.getArchiveSize());
        dataObj.setPhase(job.getPhase());
        dataObj.setPercent(job.getPercent());
        dataObj.setMessage(job.getMessage());
        dataObj.setCollectionCount(job.getCollectionCount());
        dataObj.setDocumentCount(job.getDocumentCount());
        dataObj.setObjectCount(job.getObjectCount());
        dataObj.setPluginFileCount(job.getPluginFileCount());
        dataObj.setInsertedCount(job.getInsertedCount());
        dataObj.setConflictCount(job.getConflictCount());
        dataObj.setSkippedCount(job.getSkippedCount());
        dataObj.setStartedAt(job.getStartedAt());
        dataObj.setFinishedAt(job.getFinishedAt());
        return dataObj;
    }

    public static BackupJob job(BackupJobDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        BackupJob job = new BackupJob();
        job.setId(dataObj.getId());
        job.setVersion(dataObj.getVersion());
        job.setCreateTime(dataObj.getCreateTime());
        job.setUpdateTime(dataObj.getUpdateTime());
        job.setType(dataObj.getType());
        job.setStatus(dataObj.getStatus());
        job.setTrigger(dataObj.getTrigger());
        job.setScopeTags(dataObj.getScopeTags());
        job.setPlanCode(dataObj.getPlanCode());
        job.setTargetCode(dataObj.getTargetCode());
        job.setTargetName(dataObj.getTargetName());
        job.setStrategy(dataObj.getStrategy());
        job.setArchiveName(dataObj.getArchiveName());
        job.setArchivePath(dataObj.getArchivePath());
        job.setArchiveSize(dataObj.getArchiveSize());
        job.setPhase(dataObj.getPhase());
        job.setPercent(dataObj.getPercent());
        job.setMessage(dataObj.getMessage());
        job.setCollectionCount(dataObj.getCollectionCount());
        job.setDocumentCount(dataObj.getDocumentCount());
        job.setObjectCount(dataObj.getObjectCount());
        job.setPluginFileCount(dataObj.getPluginFileCount());
        job.setInsertedCount(dataObj.getInsertedCount());
        job.setConflictCount(dataObj.getConflictCount());
        job.setSkippedCount(dataObj.getSkippedCount());
        job.setStartedAt(dataObj.getStartedAt());
        job.setFinishedAt(dataObj.getFinishedAt());
        return job;
    }

    public static RemoteTargetDO target(RemoteTarget target, CapabilityCredentialCipher cipher) {
        RemoteTargetDO dataObj = new RemoteTargetDO();
        dataObj.setId(target.getId());
        dataObj.setVersion(target.getVersion());
        dataObj.setCreateTime(target.getCreateTime());
        dataObj.setUpdateTime(target.getUpdateTime());
        dataObj.setCode(target.getCode());
        dataObj.setName(target.getName());
        dataObj.setType(target.getType());
        dataObj.setHost(target.getHost());
        dataObj.setPort(target.getPort());
        dataObj.setUsername(target.getUsername());
        dataObj.setPassword(encryptPassword(target, cipher));
        dataObj.setBasePath(target.getBasePath());
        dataObj.setPassiveMode(target.isPassiveMode());
        dataObj.setEnabled(target.isEnabled());
        return dataObj;
    }

    public static RemoteTarget target(RemoteTargetDO dataObj, CapabilityCredentialCipher cipher) {
        if (dataObj == null) {
            return null;
        }
        RemoteTarget target = new RemoteTarget();
        target.setId(dataObj.getId());
        target.setVersion(dataObj.getVersion());
        target.setCreateTime(dataObj.getCreateTime());
        target.setUpdateTime(dataObj.getUpdateTime());
        target.setCode(dataObj.getCode());
        target.setName(dataObj.getName());
        target.setType(dataObj.getType());
        target.setHost(dataObj.getHost());
        target.setPort(dataObj.getPort());
        target.setUsername(dataObj.getUsername());
        target.setPassword(decryptPassword(dataObj, cipher));
        target.setBasePath(dataObj.getBasePath());
        target.setPassiveMode(dataObj.isPassiveMode());
        target.setEnabled(dataObj.isEnabled());
        return target;
    }

    public static BackupPlanDO plan(BackupPlan plan) {
        BackupPlanDO dataObj = new BackupPlanDO();
        dataObj.setId(plan.getId());
        dataObj.setVersion(plan.getVersion());
        dataObj.setCreateTime(plan.getCreateTime());
        dataObj.setUpdateTime(plan.getUpdateTime());
        dataObj.setCode(plan.getCode());
        dataObj.setName(plan.getName());
        dataObj.setCron(plan.getCron());
        dataObj.setScopeTags(plan.getScopeTags());
        dataObj.setTargetCode(plan.getTargetCode());
        dataObj.setRetentionCount(plan.getRetentionCount());
        dataObj.setEnabled(plan.isEnabled());
        dataObj.setLastRunAt(plan.getLastRunAt());
        dataObj.setLastStatus(plan.getLastStatus());
        dataObj.setLastJobId(plan.getLastJobId());
        return dataObj;
    }

    public static BackupPlan plan(BackupPlanDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        BackupPlan plan = new BackupPlan();
        plan.setId(dataObj.getId());
        plan.setVersion(dataObj.getVersion());
        plan.setCreateTime(dataObj.getCreateTime());
        plan.setUpdateTime(dataObj.getUpdateTime());
        plan.setCode(dataObj.getCode());
        plan.setName(dataObj.getName());
        plan.setCron(dataObj.getCron());
        plan.setScopeTags(dataObj.getScopeTags());
        plan.setTargetCode(dataObj.getTargetCode());
        plan.setRetentionCount(dataObj.getRetentionCount());
        plan.setEnabled(dataObj.isEnabled());
        plan.setLastRunAt(dataObj.getLastRunAt());
        plan.setLastStatus(dataObj.getLastStatus());
        plan.setLastJobId(dataObj.getLastJobId());
        return plan;
    }

    private static String encryptPassword(RemoteTarget target, CapabilityCredentialCipher cipher) {
        if (!StringUtils.hasText(target.getPassword())) {
            return target.getPassword();
        }
        return cipher.encryptSecret(CIPHER_CODE, "target:" + target.getCode(), target.getPassword());
    }

    private static String decryptPassword(RemoteTargetDO dataObj, CapabilityCredentialCipher cipher) {
        if (!StringUtils.hasText(dataObj.getPassword())) {
            return dataObj.getPassword();
        }
        return Optional.ofNullable(dataObj.getCode()).map(code -> "target:" + code)
                .map(key -> cipher.decryptSecret(CIPHER_CODE, key, dataObj.getPassword()))
                .orElse(dataObj.getPassword());
    }
}
