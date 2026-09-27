package online.yudream.base.domain.platform.mobile.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.Locale;
import java.util.Set;

/**
 * 设备推送通道：fcm/apns 或厂商通道字符串（xiaomi/huawei/oppo/vivo 等）。
 * <p>
 * 通道是开放集合，统一归一化为小写字母/数字/连字符后存储，便于按通道路由发送端口。
 */
public record MobilePushChannel(String value) {

    public static final String FCM = "fcm";
    public static final String APNS = "apns";

    private static final Set<String> KNOWN_CHANNELS = Set.of(FCM, APNS, "xiaomi", "huawei", "oppo", "vivo", "honor", "meizu");

    public MobilePushChannel {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9-]{1,31}")) {
            throw new BizException("推送通道格式非法：" + value);
        }
        value = normalized;
    }

    public static MobilePushChannel of(String value) {
        return new MobilePushChannel(value);
    }

    public boolean isKnown() {
        return KNOWN_CHANNELS.contains(value);
    }
}
