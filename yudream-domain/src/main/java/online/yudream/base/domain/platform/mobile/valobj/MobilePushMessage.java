package online.yudream.base.domain.platform.mobile.valobj;

import java.util.Map;

/**
 * 一次推送发送请求的值对象：按 pushChannel 路由到具体发送端口实现。
 */
public record MobilePushMessage(
        String pushChannel,
        String pushToken,
        String title,
        String body,
        Map<String, String> data
) {
    public MobilePushMessage {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static MobilePushMessage of(String pushChannel, String pushToken, String title, String body) {
        return new MobilePushMessage(pushChannel, pushToken, title, body, Map.of());
    }
}
