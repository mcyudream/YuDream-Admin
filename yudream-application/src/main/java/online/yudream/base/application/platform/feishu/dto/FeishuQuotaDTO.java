package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 当前用户的飞书云文档调用额度快照（分钟窗口 + 日窗口）。
 */
@Data
@Builder
public class FeishuQuotaDTO {
    /** 用户 ID 字符串，保持长 ID 精度。 */
    private String userId;
    private String minuteKey;
    private long minuteUsed;
    private long minuteLimit;
    private String dailyKey;
    private long dailyUsed;
    private long dailyLimit;
}
