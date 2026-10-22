package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class WorkspaceConfigRequest {
    @Min(value = 1, message = "工作区配额至少 1 MB")
    private long quotaMb;
    @Min(value = 1, message = "tmp/ 清理天数至少 1 天")
    @Max(value = 365, message = "tmp/ 清理天数最多 365 天")
    private int tmpRetentionDays;
}
