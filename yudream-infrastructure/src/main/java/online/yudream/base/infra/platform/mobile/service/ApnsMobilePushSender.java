package online.yudream.base.infra.platform.mobile.service;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.mobile.service.MobilePushSender;
import online.yudream.base.domain.platform.mobile.valobj.MobilePushMessage;
import online.yudream.base.domain.platform.mobile.valobj.MobilePushSendResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * APNs 推送发送空实现：占住按通道路由的发送端口，只记录日志并返回未配置，
 * 不连接 Apple 推送服务。真实接入待后续迭代。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mobile-app", name = "enabled", havingValue = "true")
public class ApnsMobilePushSender implements MobilePushSender {

    @Override
    public boolean supports(String pushChannel) {
        return "apns".equals(pushChannel);
    }

    @Override
    public MobilePushSendResult send(MobilePushMessage message) {
        log.info("APNs 推送未配置：token={}, title={}", message.pushToken(), message.title());
        return MobilePushSendResult.notConfigured("APNs 推送通道尚未配置");
    }
}
