package online.yudream.base.interfaces.system.backup.res;

import lombok.Builder;
import lombok.Data;

/** 备份范围响应。 */
@Data
@Builder
public class BackupScopeRes {
    private String tag;
    private String type;
    private String pluginCode;
    private String scopeCode;
    private String displayName;
    private String description;
    private String defaultSchedule;
}
