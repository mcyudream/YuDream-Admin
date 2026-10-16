package online.yudream.base.application.platform.feishu.cmd;

import lombok.Builder;
import lombok.Data;

/**
 * 更新飞书调用额度上限：为空表示保持当前值。
 */
@Data
@Builder
public class FeishuQuotaConfigCmd {
    private Integer defaultPerMinuteLimit;
    private Integer defaultDailyLimit;
}
