package online.yudream.base.domain.platform.mobile.service;

import online.yudream.base.domain.platform.mobile.valobj.MobilePushMessage;
import online.yudream.base.domain.platform.mobile.valobj.MobilePushSendResult;

/**
 * 推送发送端口：实现方按 pushChannel 声明自己承接的通道（fcm/apns/厂商通道）。
 * <p>
 * 实现是纯工具包装：不得在构造或发送前建立长驻外部连接；第一迭代仅提供
 * FCM/APNs 空实现占位，返回未配置结果。
 */
public interface MobilePushSender {

    /** 是否承接该推送通道（入参为归一化后的小写通道字符串）。 */
    boolean supports(String pushChannel);

    MobilePushSendResult send(MobilePushMessage message);
}
