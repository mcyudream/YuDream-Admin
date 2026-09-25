package online.yudream.base.interfaces.system.backup.request;

import lombok.Data;

import java.util.List;

/** 创建/更新备份计划请求。 */
@Data
public class BackupPlanRequest {
    private String code;
    private String name;
    /** Spring cron 6 位表达式（秒 分 时 日 月 周），如 0 0 3 * * *。 */
    private String cron;
    private List<String> scopeTags;
    private String targetCode;
    private Integer retentionCount;
}
