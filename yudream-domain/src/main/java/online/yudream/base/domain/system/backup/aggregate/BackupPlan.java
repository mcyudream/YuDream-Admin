package online.yudream.base.domain.system.backup.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.system.backup.enumerate.BackupJobStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 备份计划：按 cron 周期把指定范围的全量归档推送到某个异地目标。
 * 范围 tag 与任务一致：{@code system} 或 {@code plugin:{pluginCode}/{scopeCode}}。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BackupPlan extends BaseDomain {

    private static final Pattern CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    public static final int DEFAULT_RETENTION = 10;
    private static final int MAX_RETENTION = 100;

    private String code;
    private String name;
    private String cron;
    private List<String> scopeTags;
    private String targetCode;
    private int retentionCount;
    private boolean enabled;
    private LocalDateTime lastRunAt;
    private BackupJobStatus lastStatus;
    private Long lastJobId;

    public static BackupPlan create(String code, String name, String cron, List<String> scopeTags,
                                    String targetCode, Integer retentionCount) {
        BackupPlan plan = BackupPlan.builder()
                .code(code)
                .name(name)
                .cron(cron)
                .scopeTags(scopeTags)
                .targetCode(targetCode)
                .retentionCount(retentionCount == null ? DEFAULT_RETENTION : retentionCount)
                .enabled(true)
                .build();
        plan.ensureValid();
        return plan;
    }

    public void update(String name, String cron, List<String> scopeTags, String targetCode, Integer retentionCount) {
        this.name = name;
        this.cron = cron;
        this.scopeTags = scopeTags;
        this.targetCode = targetCode;
        if (retentionCount != null) {
            this.retentionCount = retentionCount;
        }
        ensureValid();
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void markRun(Long jobId, BackupJobStatus status, LocalDateTime at) {
        this.lastJobId = jobId;
        this.lastStatus = status;
        this.lastRunAt = at;
    }

    /** 域不变量校验。cron 表达式语法由应用层结合调度器校验。 */
    public void ensureValid() {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new BizException("计划编码须为小写字母/数字/连字符且以字母或数字开头：" + code);
        }
        if (name == null || name.isBlank()) {
            throw new BizException("请填写计划名称");
        }
        if (cron == null || cron.isBlank()) {
            throw new BizException("请填写 cron 表达式");
        }
        if (scopeTags == null || scopeTags.isEmpty()) {
            throw new BizException("请至少选择一个备份范围");
        }
        if (targetCode == null || targetCode.isBlank()) {
            throw new BizException("请选择异地目标");
        }
        if (retentionCount < 1 || retentionCount > MAX_RETENTION) {
            throw new BizException("保留份数须在 1-" + MAX_RETENTION + " 之间");
        }
    }
}
