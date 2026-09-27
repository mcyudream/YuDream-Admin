package online.yudream.base.domain.platform.mobile.valobj;

/**
 * 推送发送结果。第一迭代只有空实现：accepted=false、configured=false，
 * 用于占住按通道路由的发送端口，待后续迭代接入真实 FCM/APNs/厂商通道。
 */
public record MobilePushSendResult(
        boolean accepted,
        boolean configured,
        String detail
) {

    public static MobilePushSendResult ok() {
        return new MobilePushSendResult(true, true, "已受理");
    }

    public static MobilePushSendResult rejected(String detail) {
        return new MobilePushSendResult(false, true, detail);
    }

    public static MobilePushSendResult notConfigured(String detail) {
        return new MobilePushSendResult(false, false, detail);
    }
}
