package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 插件市场源能力：本机 LOCAL 源、自托管发布/审核与公开社区。
 * 远程源订阅不依赖本能力；项目闸门关闭时不播种本机源、不注册发布/公开端点；无 Nexus 隐式回落。
 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.plugin-market-source", name = "enabled", havingValue = "true")
public class PluginMarketSourceCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "plugin-market-source";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "插件市场源",
                CapabilityType.DISTRIBUTION,
                "本机插件市场源：播种 LOCAL 源、自托管发布/审核，并向其他实例提供公开社区与 v2 协议",
                "i-ri:store-2-line",
                48,
                Map.of(),
                List.of()
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("插件市场源未启用");
        }
        return CapabilityHealth.enabled("插件市场源能力正常", Map.of());
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
            return CapabilityTestResult.failure("插件市场源未启用");
        }
        return CapabilityTestResult.success("插件市场源能力可用");
    }
}
