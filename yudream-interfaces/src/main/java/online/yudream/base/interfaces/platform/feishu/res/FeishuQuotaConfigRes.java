package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 飞书调用额度配置响应。
 */
@Data
@Builder
public class FeishuQuotaConfigRes implements Serializable {
    private int defaultPerMinuteLimit;
    private int defaultDailyLimit;
}
