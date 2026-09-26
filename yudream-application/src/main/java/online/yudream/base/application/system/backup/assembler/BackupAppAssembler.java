package online.yudream.base.application.system.backup.assembler;

import online.yudream.base.application.system.backup.dto.BackupJobDTO;
import online.yudream.base.application.system.backup.dto.BackupPlanDTO;
import online.yudream.base.application.system.backup.dto.BackupScopeDTO;
import online.yudream.base.application.system.backup.dto.RemoteTargetDTO;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.service.PluginBackupScopeSource;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import org.springframework.util.StringUtils;

import java.util.List;

/** 备份域领域对象到应用 DTO 装配；Long ID 转字符串。 */
public final class BackupAppAssembler {

    private BackupAppAssembler() {
    }

    public static BackupJobDTO toJobDTO(BackupJob job) {
        return new BackupJobDTO(
                String.valueOf(job.getId()),
                job.getType() == null ? null : job.getType().name(),
                job.getStatus() == null ? null : job.getStatus().name(),
                job.getTrigger() == null ? null : job.getTrigger().name(),
                job.getScopeTags(),
                job.getPlanCode(),
                job.getTargetCode(),
                job.getTargetName(),
                job.getStrategy() == null ? null : job.getStrategy().name(),
                job.getArchiveName(),
                job.getArchiveSize(),
                job.getPhase(),
                job.getPercent(),
                job.getMessage(),
                job.getCollectionCount(),
                job.getDocumentCount(),
                job.getObjectCount(),
                job.getPluginFileCount(),
                job.getInsertedCount(),
                job.getConflictCount(),
                job.getSkippedCount(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getCreateTime());
    }

    public static List<BackupJobDTO> toJobDTOs(List<BackupJob> jobs) {
        return jobs.stream().map(BackupAppAssembler::toJobDTO).toList();
    }

    public static BackupScopeDTO toSystemScopeDTO() {
        return new BackupScopeDTO(BackupScopeRef.system().tag(), "SYSTEM", null, "system",
                "系统数据", "Mongo 全部业务集合 + 对象存储全部文件", "");
    }

    public static BackupScopeDTO toScopeDTO(PluginScopeSourceHandle handle) {
        return new BackupScopeDTO(handle.ref().tag(), "PLUGIN", handle.pluginCode(), handle.scopeCode(),
                StringUtils.hasText(handle.displayName()) ? handle.displayName() : handle.ref().tag(),
                handle.description(), handle.defaultSchedule());
    }

    /** 装配入参：插件范围句柄 + 备份范围引用。 */
    public record PluginScopeSourceHandle(BackupScopeRef ref, String pluginCode, String scopeCode,
                                          String displayName, String description, String defaultSchedule) {

        public static PluginScopeSourceHandle of(PluginBackupScopeSource.PluginScopeHandle handle) {
            return new PluginScopeSourceHandle(
                    BackupScopeRef.plugin(handle.pluginCode(), handle.scopeCode(), handle.displayName()),
                    handle.pluginCode(), handle.scopeCode(), handle.displayName(),
                    handle.description(), handle.defaultSchedule());
        }
    }

    public static RemoteTargetDTO toTargetDTO(RemoteTarget target) {
        return new RemoteTargetDTO(
                String.valueOf(target.getId()),
                target.getCode(),
                target.getName(),
                target.getType() == null ? null : target.getType().name(),
                target.getHost(),
                target.getPort(),
                target.getUsername(),
                target.getBasePath(),
                target.isPassiveMode(),
                target.isInsecureTls(),
                target.isEnabled(),
                StringUtils.hasText(target.getPassword()),
                target.getCreateTime());
    }

    public static List<RemoteTargetDTO> toTargetDTOs(List<RemoteTarget> targets) {
        return targets.stream().map(BackupAppAssembler::toTargetDTO).toList();
    }

    public static BackupPlanDTO toPlanDTO(BackupPlan plan, String targetName) {
        return new BackupPlanDTO(
                String.valueOf(plan.getId()),
                plan.getCode(),
                plan.getName(),
                plan.getCron(),
                plan.getScopeTags(),
                plan.getTargetCode(),
                targetName,
                plan.getRetentionCount(),
                plan.isEnabled(),
                plan.getLastRunAt(),
                plan.getLastStatus() == null ? null : plan.getLastStatus().name(),
                plan.getLastJobId() == null ? null : String.valueOf(plan.getLastJobId()));
    }

    public static List<BackupPlanDTO> toPlanDTOs(List<BackupPlan> plans, java.util.function.Function<String, String> targetNameOf) {
        return plans.stream().map(plan -> toPlanDTO(plan, targetNameOf.apply(plan.getTargetCode()))).toList();
    }
}
