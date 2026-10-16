package online.yudream.base.domain.platform.feishu.valobj;

import online.yudream.base.domain.platform.feishu.enumerate.FeishuQuotaScope;

/**
 * 飞书调用额度快照：当前用户在当前分钟窗口与日窗口的已用与上限。
 */
public record FeishuQuotaSnapshot(
        Long userId,
        FeishuQuotaScope scope,
        String minuteKey,
        long minuteUsed,
        long minuteLimit,
        String dayKey,
        long dayUsed,
        long dayLimit
) {

    public long minuteRemaining() {
        return Math.max(0, minuteLimit - minuteUsed);
    }

    public long dayRemaining() {
        return Math.max(0, dayLimit - dayUsed);
    }
}
