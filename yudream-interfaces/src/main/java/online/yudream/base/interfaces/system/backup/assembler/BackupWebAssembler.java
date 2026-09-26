package online.yudream.base.interfaces.system.backup.assembler;

import online.yudream.base.application.system.backup.cmd.BackupPlanCmd;
import online.yudream.base.application.system.backup.cmd.RemoteTargetCmd;
import online.yudream.base.application.system.backup.dto.BackupAnalysisDTO;
import online.yudream.base.application.system.backup.dto.BackupJobDTO;
import online.yudream.base.application.system.backup.dto.BackupPlanDTO;
import online.yudream.base.application.system.backup.dto.BackupScopeDTO;
import online.yudream.base.application.system.backup.dto.RemoteTargetDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.enumerate.RemoteTargetType;
import online.yudream.base.domain.system.backup.valobj.RemoteEntry;
import online.yudream.base.interfaces.system.backup.request.RemoteTargetRequest;
import online.yudream.base.interfaces.system.backup.res.BackupAnalysisRes;
import online.yudream.base.interfaces.system.backup.res.BackupJobRes;
import online.yudream.base.interfaces.system.backup.res.BackupPlanRes;
import online.yudream.base.interfaces.system.backup.res.BackupScopeRes;
import online.yudream.base.interfaces.system.backup.res.RemoteArchiveRes;
import online.yudream.base.interfaces.system.backup.res.RemoteTargetRes;

import java.util.List;

/** 备份域 Web 装配：request -> cmd、应用 DTO -> res。 */
public final class BackupWebAssembler {

    private BackupWebAssembler() {
    }

    // ---------------------------------------------------------------- request -> cmd

    public static RemoteTargetCmd toCmd(RemoteTargetRequest request) {
        return new RemoteTargetCmd(request.getCode(), request.getName(), parseType(request.getType()),
                request.getHost(), request.getPort(), request.getUsername(), request.getPassword(),
                request.getBasePath(), Boolean.TRUE.equals(request.getPassiveMode()),
                Boolean.TRUE.equals(request.getInsecureTls()));
    }

    public static BackupPlanCmd toCmd(online.yudream.base.interfaces.system.backup.request.BackupPlanRequest request) {
        return new BackupPlanCmd(request.getCode(), request.getName(), request.getCron(),
                request.getScopeTags(), request.getTargetCode(), request.getRetentionCount());
    }

    public static BackupConflictStrategy parseStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            throw new BizException("请选择合并策略（以哪边为准）");
        }
        try {
            return BackupConflictStrategy.valueOf(strategy);
        } catch (IllegalArgumentException e) {
            throw new BizException("非法的合并策略：" + strategy);
        }
    }

    private static RemoteTargetType parseType(String type) {
        if (type == null || type.isBlank()) {
            throw new BizException("请选择目标协议类型");
        }
        try {
            return RemoteTargetType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new BizException("非法的目标协议类型：" + type);
        }
    }

    // ---------------------------------------------------------------- DTO -> res

    public static BackupJobRes toRes(BackupJobDTO dto) {
        return BackupJobRes.builder()
                .id(dto.id())
                .type(dto.type())
                .status(dto.status())
                .trigger(dto.trigger())
                .scopeTags(dto.scopeTags())
                .planCode(dto.planCode())
                .targetCode(dto.targetCode())
                .targetName(dto.targetName())
                .strategy(dto.strategy())
                .archiveName(dto.archiveName())
                .archiveSize(dto.archiveSize())
                .phase(dto.phase())
                .percent(dto.percent())
                .message(dto.message())
                .collectionCount(dto.collectionCount())
                .documentCount(dto.documentCount())
                .objectCount(dto.objectCount())
                .pluginFileCount(dto.pluginFileCount())
                .insertedCount(dto.insertedCount())
                .conflictCount(dto.conflictCount())
                .skippedCount(dto.skippedCount())
                .startedAt(dto.startedAt())
                .finishedAt(dto.finishedAt())
                .createTime(dto.createTime())
                .build();
    }

    public static List<BackupJobRes> toJobResList(List<BackupJobDTO> items) {
        return items.stream().map(BackupWebAssembler::toRes).toList();
    }

    public static BackupScopeRes toRes(BackupScopeDTO dto) {
        return BackupScopeRes.builder()
                .tag(dto.tag())
                .type(dto.type())
                .pluginCode(dto.pluginCode())
                .scopeCode(dto.scopeCode())
                .displayName(dto.displayName())
                .description(dto.description())
                .defaultSchedule(dto.defaultSchedule())
                .build();
    }

    public static List<BackupScopeRes> toScopeResList(List<BackupScopeDTO> items) {
        return items.stream().map(BackupWebAssembler::toRes).toList();
    }

    public static RemoteTargetRes toRes(RemoteTargetDTO dto) {
        return RemoteTargetRes.builder()
                .id(dto.id())
                .code(dto.code())
                .name(dto.name())
                .type(dto.type())
                .host(dto.host())
                .port(dto.port())
                .username(dto.username())
                .basePath(dto.basePath())
                .passiveMode(dto.passiveMode())
                .insecureTls(dto.insecureTls())
                .enabled(dto.enabled())
                .passwordSet(dto.passwordSet())
                .createTime(dto.createTime())
                .build();
    }

    public static List<RemoteTargetRes> toTargetResList(List<RemoteTargetDTO> items) {
        return items.stream().map(BackupWebAssembler::toRes).toList();
    }

    public static BackupPlanRes toRes(BackupPlanDTO dto) {
        return BackupPlanRes.builder()
                .id(dto.id())
                .code(dto.code())
                .name(dto.name())
                .cron(dto.cron())
                .scopeTags(dto.scopeTags())
                .targetCode(dto.targetCode())
                .targetName(dto.targetName())
                .retentionCount(dto.retentionCount())
                .enabled(dto.enabled())
                .lastRunAt(dto.lastRunAt())
                .lastStatus(dto.lastStatus())
                .lastJobId(dto.lastJobId())
                .build();
    }

    public static List<BackupPlanRes> toPlanResList(List<BackupPlanDTO> items) {
        return items.stream().map(BackupWebAssembler::toRes).toList();
    }

    public static BackupAnalysisRes toRes(BackupAnalysisDTO dto) {
        return BackupAnalysisRes.builder()
                .hostVersion(dto.hostVersion())
                .createdAt(dto.createdAt())
                .masterKeyFingerprint(dto.masterKeyFingerprint())
                .masterKeyMatch(dto.masterKeyMatch())
                .collections(dto.collections().stream().map(item -> BackupAnalysisRes.CollectionAnalysis.builder()
                        .name(item.name())
                        .archiveCount(item.archiveCount())
                        .missingCount(item.missingCount())
                        .conflictCount(item.conflictCount())
                        .build()).toList())
                .objects(BackupAnalysisRes.ObjectAnalysis.builder()
                        .archiveCount(dto.objects().archiveCount())
                        .missingCount(dto.objects().missingCount())
                        .conflictCount(dto.objects().conflictCount())
                        .totalBytes(dto.objects().totalBytes())
                        .build())
                .pluginScopes(dto.pluginScopes().stream().map(item -> BackupAnalysisRes.PluginScopeAnalysis.builder()
                        .pluginCode(item.pluginCode())
                        .scopeCode(item.scopeCode())
                        .displayName(item.displayName())
                        .fileCount(item.fileCount())
                        .available(item.available())
                        .build()).toList())
                .warnings(dto.warnings())
                .build();
    }

    public static List<RemoteArchiveRes> toArchiveResList(List<RemoteEntry> entries) {
        return entries.stream().map(entry -> RemoteArchiveRes.builder()
                .name(entry.name())
                .size(entry.size())
                .modifiedAtMillis(entry.modifiedAtMillis())
                .build()).toList();
    }
}
