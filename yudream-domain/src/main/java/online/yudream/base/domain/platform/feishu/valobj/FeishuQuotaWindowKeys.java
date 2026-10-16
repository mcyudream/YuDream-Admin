package online.yudream.base.domain.platform.feishu.valobj;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 飞书调用额度的窗口键：一次预占同时落在分钟窗口与日窗口。
 * <p>
 * 窗口按上海时区分账（与平台其他额度一致）；窗口键是持久化计数桶的组成部分，
 * 退还额度必须携带预占时使用的窗口键，跨窗口边界不会退还到别的桶。
 */
public record FeishuQuotaWindowKeys(String minuteKey, String dayKey) {

    public static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(QUOTA_ZONE);
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(QUOTA_ZONE);

    public static FeishuQuotaWindowKeys of(Instant instant) {
        Instant moment = instant == null ? Instant.now() : instant;
        return new FeishuQuotaWindowKeys(MINUTE_FORMAT.format(moment), DAY_FORMAT.format(moment));
    }
}
