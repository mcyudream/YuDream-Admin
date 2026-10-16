package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 飞书调用额度配置：所有用户共享的分钟/日上限（每用户分别记账）。
 */
@Data
@Builder
public class FeishuQuotaConfigDTO {
    private int defaultPerMinuteLimit;
    private int defaultDailyLimit;
}
