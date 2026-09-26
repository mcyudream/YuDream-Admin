package online.yudream.base.application.system.backup.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.backup.assembler.BackupAppAssembler;
import online.yudream.base.application.system.backup.cmd.BackupPlanCmd;
import online.yudream.base.application.system.backup.cmd.RemoteTargetCmd;
import online.yudream.base.application.system.backup.dto.BackupPlanDTO;
import online.yudream.base.application.system.backup.dto.RemoteTargetDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.backup.aggregate.BackupJob;
import online.yudream.base.domain.system.backup.aggregate.BackupPlan;
import online.yudream.base.domain.system.backup.aggregate.RemoteTarget;
import online.yudream.base.domain.system.backup.enumerate.BackupConflictStrategy;
import online.yudream.base.domain.system.backup.enumerate.BackupJobTrigger;
import online.yudream.base.domain.system.backup.enumerate.BackupJobType;
import online.yudream.base.domain.system.backup.enumerate.RemoteTargetType;
import online.yudream.base.domain.system.backup.event.BackupPlansChangedEvent;
import online.yudream.base.domain.system.backup.repo.BackupJobRepo;
import online.yudream.base.domain.system.backup.repo.BackupPlanRepo;
import online.yudream.base.domain.system.backup.repo.RemoteTargetRepo;
import online.yudream.base.domain.system.backup.service.BackupPlanRunner;
import online.yudream.base.domain.system.backup.service.RemoteBackupStorage;
import online.yudream.base.domain.system.backup.valobj.BackupScopeRef;
import online.yudream.base.domain.system.backup.valobj.RemoteEntry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 异地备份应用服务：目标与计划的 CRUD/启停/连通性测试、手动触发、远端归档列表与恢复入队。
 */
@Service
@RequiredArgsConstructor
public class RemoteBackupAppService {

    private final RemoteTargetRepo targetRepo;
    private final BackupPlanRepo planRepo;
    private final BackupJobRepo jobRepo;
    private final RemoteBackupStorage.Factory remoteFactory;
    private final BackupPlanRunner planRunner;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- 目标

    public List<RemoteTargetDTO> targets() {
        return BackupAppAssembler.toTargetDTOs(targetRepo.findAll());
    }

    public RemoteTargetDTO createTarget(RemoteTargetCmd cmd) {
        if (targetRepo.findByCode(cmd.code()).isPresent()) {
            throw new BizException("目标编码已存在：" + cmd.code());
        }
        RemoteTarget target = RemoteTarget.create(cmd.code(), cmd.name(), requireType(cmd.type()),
                cmd.host(), cmd.port(), cmd.username(), cmd.password(), cmd.basePath(), cmd.passiveMode(),
                Boolean.TRUE.equals(cmd.insecureTls()));
        return BackupAppAssembler.toTargetDTO(targetRepo.save(target));
    }

    public RemoteTargetDTO updateTarget(Long id, RemoteTargetCmd cmd) {
        RemoteTarget target = targetRepo.findById(id).orElseThrow(() -> new BizException("异地目标不存在"));
        target.update(cmd.name(), requireType(cmd.type()), cmd.host(), cmd.port(),
                cmd.username(), cmd.password(), cmd.basePath(), cmd.passiveMode(),
                Boolean.TRUE.equals(cmd.insecureTls()));
        return BackupAppAssembler.toTargetDTO(targetRepo.save(target));
    }

    public void deleteTarget(Long id) {
        RemoteTarget target = targetRepo.findById(id).orElseThrow(() -> new BizException("异地目标不存在"));
        boolean referenced = planRepo.findAll().stream().anyMatch(plan -> plan.getTargetCode().equals(target.getCode()));
        if (referenced) {
            throw new BizException("该目标仍被备份计划引用，请先删除对应计划");
        }
        targetRepo.deleteById(id);
    }

    public void enableTarget(Long id) {
        RemoteTarget target = requireTarget(id);
        target.enable();
        targetRepo.save(target);
    }

    public void disableTarget(Long id) {
        RemoteTarget target = requireTarget(id);
        target.disable();
        targetRepo.save(target);
    }

    public void testTarget(Long id) {
        remoteFactory.create(requireTarget(id)).test();
    }

    /** 远端归档清单（按修改时间倒序）。 */
    public List<RemoteEntry> remoteArchives(Long targetId) {
        RemoteTarget target = requireTarget(targetId);
        List<RemoteEntry> entries = new ArrayList<>(remoteFactory.create(target).list(archivePrefix()));
        entries.sort((a, b) -> Long.compare(
                b.modifiedAtMillis() == null ? 0 : b.modifiedAtMillis(),
                a.modifiedAtMillis() == null ? 0 : a.modifiedAtMillis()));
        return entries;
    }

    /** 从远端归档创建恢复任务。 */
    public void createRestoreJob(Long targetId, String archiveName, BackupConflictStrategy strategy) {
        RemoteTarget target = requireTarget(targetId);
        if (!StringUtils.hasText(archiveName)) {
            throw new BizException("请选择要恢复的远端归档");
        }
        if (strategy == null) {
            throw new BizException("请先选择合并策略（以哪边为准）");
        }
        BackupJob job = BackupJob.create(BackupJobType.REMOTE_RESTORE, BackupJobTrigger.MANUAL,
                List.of(), strategy, null, target.getCode(), target.getName());
        job.setArchiveName(archiveName);
        jobRepo.save(job);
    }

    // ---------------------------------------------------------------- 计划

    public List<BackupPlanDTO> plans() {
        Map<String, String> targetNames = targetRepo.findAll().stream()
                .collect(Collectors.toMap(RemoteTarget::getCode, target ->
                        StringUtils.hasText(target.getName()) ? target.getName() : target.getCode()));
        return BackupAppAssembler.toPlanDTOs(planRepo.findAll(), targetNames::get);
    }

    public BackupPlanDTO createPlan(BackupPlanCmd cmd) {
        validateCron(cmd.cron());
        validateScopeTags(cmd.scopeTags());
        requireTargetCode(cmd.targetCode());
        if (planRepo.findByCode(cmd.code()).isPresent()) {
            throw new BizException("计划编码已存在：" + cmd.code());
        }
        BackupPlan plan = BackupPlan.create(cmd.code(), cmd.name(), cmd.cron(),
                cmd.scopeTags(), cmd.targetCode(), cmd.retentionCount());
        BackupPlan saved = planRepo.save(plan);
        events.publishEvent(new BackupPlansChangedEvent());
        return planDTO(saved);
    }

    public BackupPlanDTO updatePlan(Long id, BackupPlanCmd cmd) {
        BackupPlan plan = planRepo.findById(id).orElseThrow(() -> new BizException("备份计划不存在"));
        validateCron(cmd.cron());
        validateScopeTags(cmd.scopeTags());
        requireTargetCode(cmd.targetCode());
        plan.update(cmd.name(), cmd.cron(), cmd.scopeTags(), cmd.targetCode(), cmd.retentionCount());
        BackupPlan saved = planRepo.save(plan);
        events.publishEvent(new BackupPlansChangedEvent());
        return planDTO(saved);
    }

    public void deletePlan(Long id) {
        requirePlan(id);
        planRepo.deleteById(id);
        events.publishEvent(new BackupPlansChangedEvent());
    }

    public void enablePlan(Long id) {
        BackupPlan plan = requirePlan(id);
        validateCron(plan.getCron());
        plan.enable();
        planRepo.save(plan);
        events.publishEvent(new BackupPlansChangedEvent());
    }

    public void disablePlan(Long id) {
        BackupPlan plan = requirePlan(id);
        plan.disable();
        planRepo.save(plan);
        events.publishEvent(new BackupPlansChangedEvent());
    }

    /** 手动触发一次计划对应的异地备份。 */
    public void runPlan(Long id) {
        BackupPlan plan = requirePlan(id);
        planRunner.runPlan(plan, BackupJobTrigger.MANUAL);
    }

    // ---------------------------------------------------------------- 内部

    private RemoteTarget requireTarget(Long id) {
        return targetRepo.findById(id).orElseThrow(() -> new BizException("异地目标不存在"));
    }

    private BackupPlan requirePlan(Long id) {
        return planRepo.findById(id).orElseThrow(() -> new BizException("备份计划不存在"));
    }

    private void requireTargetCode(String targetCode) {
        if (!StringUtils.hasText(targetCode) || targetRepo.findByCode(targetCode).isEmpty()) {
            throw new BizException("异地目标不存在：" + targetCode);
        }
    }

    private RemoteTargetType requireType(RemoteTargetType type) {
        if (type == null) {
            throw new BizException("请选择目标协议类型");
        }
        return type;
    }

    private void validateCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            return;
        }
        try {
            if (CronExpression.parse(cron).next(LocalDateTime.now()) == null) {
                throw new IllegalArgumentException("no next execution");
            }
        } catch (IllegalArgumentException e) {
            throw new BizException("cron 表达式非法（6 位：秒 分 时 日 月 周）：" + cron);
        }
    }

    private void validateScopeTags(List<String> scopeTags) {
        if (scopeTags == null || scopeTags.isEmpty()) {
            throw new BizException("请至少选择一个备份范围");
        }
        scopeTags.forEach(tag -> BackupScopeRef.parse(tag));
    }

    private BackupPlanDTO planDTO(BackupPlan plan) {
        String targetName = targetRepo.findByCode(plan.getTargetCode())
                .map(target -> StringUtils.hasText(target.getName()) ? target.getName() : target.getCode())
                .orElse(plan.getTargetCode());
        return BackupAppAssembler.toPlanDTO(plan, targetName);
    }

    static String archivePrefix() {
        return "yudream-backup-";
    }
}
