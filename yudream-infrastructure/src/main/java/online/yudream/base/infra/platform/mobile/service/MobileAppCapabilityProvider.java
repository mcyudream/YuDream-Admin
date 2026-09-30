package online.yudream.base.infra.platform.mobile.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.domain.platform.mobile.valobj.MobileCapabilityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 移动应用能力：为 React Native App 提供插件移动清单聚合与设备注册/push 通道模型。
 * <p>
 * 项目闸门关闭时不注册移动端点、不启动推送发送器；应用闸门关闭时各用例
 * 在应用层 ensureEnabled 拒绝。iosEnabled 只播种空配置行，读取处回落 false。
 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mobile-app", name = "enabled", havingValue = "true")
public class MobileAppCapabilityProvider implements CapabilityProvider {

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                MobileCapabilityConfig.CAPABILITY_CODE,
                "移动应用",
                CapabilityType.DISTRIBUTION,
                "为移动 App 提供插件移动清单/移动产物分发、设备注册与推送通道模型",
                "i-ri:smartphone-line",
                47,
                Map.of(MobileCapabilityConfig.KEY_IOS_ENABLED, ""),
                List.of()
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("移动应用能力未启用");
        }
        return CapabilityHealth.enabled("移动应用能力正常", Map.of());
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("移动应用能力未启用");
        }
        return CapabilityTestResult.success("移动应用能力可用");
    }
}
