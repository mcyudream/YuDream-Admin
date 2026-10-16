package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 当前用户飞书调用额度响应。
 */
@Data
@Builder
public class FeishuQuotaRes implements Serializable {
    private String userId;
    private String minuteKey;
    private long minuteUsed;
    private long minuteLimit;
    private String dailyKey;
    private long dailyUsed;
    private long dailyLimit;
}
