package online.yudream.base.application.platform.mobile.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.mobile.aggregate.MobileDevice;
import online.yudream.base.domain.platform.mobile.repo.MobileDeviceRepo;
import online.yudream.base.domain.platform.mobile.service.MobilePushSender;
import online.yudream.base.domain.platform.mobile.valobj.MobilePushMessage;
import online.yudream.base.domain.platform.mobile.valobj.MobilePushSendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送路由：按设备 pushChannel 选择承接的 {@link MobilePushSender} 发送。
 * <p>
 * 第一迭代仅占住按通道路由的接口位置（FCM/APNs 为空实现）；无发送器承接的通道
 * 返回未路由结果，不抛异常，避免影响注册等主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobilePushDispatchService {

    private final MobileDeviceRepo mobileDeviceRepo;
    private final List<MobilePushSender> senders;

    /**
     * 向用户全部设备推送；返回 deviceId -> 发送结果（id 为 string，遵循 Long ID 边界约定）。
     */
    public Map<String, MobilePushSendResult> dispatchToUser(Long userId, String title, String body, Map<String, String> data) {
        Map<String, MobilePushSendResult> results = new LinkedHashMap<>();
        if (userId == null) {
            return results;
        }
        for (MobileDevice device : mobileDeviceRepo.findByUserId(userId)) {
            results.put(device.getId() == null ? "" : String.valueOf(device.getId()), dispatch(device, title, body, data));
        }
        return results;
    }

    public MobilePushSendResult dispatch(MobileDevice device, String title, String body, Map<String, String> data) {
        if (device == null || !StringUtils.hasText(device.getPushChannel())) {
            return MobilePushSendResult.rejected("设备缺少推送通道");
        }
        MobilePushSender sender = senders.stream()
                .filter(candidate -> candidate.supports(device.getPushChannel()))
                .findFirst()
                .orElse(null);
        if (sender == null) {
            MobilePushSendResult result = MobilePushSendResult.notConfigured("暂无承接通道 " + device.getPushChannel() + " 的发送实现");
            log.info("推送未路由：userId={}, channel={}, detail={}", device.getUserId(), device.getPushChannel(), result.detail());
            return result;
        }
        MobilePushSendResult result = sender.send(new MobilePushMessage(
                device.getPushChannel(), device.getPushToken(), title, body, data));
        log.info("推送发送：userId={}, channel={}, accepted={}, detail={}",
                device.getUserId(), device.getPushChannel(), result.accepted(), result.detail());
        return result;
    }

    /** 当前已注册的发送器承接通道，供诊断展示。 */
    public List<String> supportedChannels() {
        List<String> channels = new ArrayList<>();
        for (MobilePushSender sender : senders) {
            for (String channel : List.of("fcm", "apns", "xiaomi", "huawei", "oppo", "vivo")) {
                if (sender.supports(channel) && !channels.contains(channel)) {
                    channels.add(channel);
                }
            }
        }
        return channels;
    }
}
