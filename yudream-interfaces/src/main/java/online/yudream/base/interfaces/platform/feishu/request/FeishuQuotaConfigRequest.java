package online.yudream.base.interfaces.platform.feishu.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 飞书调用额度上限请求：为空表示保持当前值。
 */
@Data
public class FeishuQuotaConfigRequest {
    @Min(value = 1, message = "每分钟上限必须大于 0")
    private Integer defaultPerMinuteLimit;
    @Min(value = 1, message = "每日上限必须大于 0")
    private Integer defaultDailyLimit;
}
