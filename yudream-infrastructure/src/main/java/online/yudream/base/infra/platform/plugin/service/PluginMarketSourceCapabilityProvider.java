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
 * 插件市场源能力：多源订阅、自托管发布与审核。项目闸门关闭时不播种本机源、管理端点不注册；
 * 应用闸门关闭时市场目录为空，无 Nexus 隐式回落；插件管理、上传与回滚不受影响。
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
                "自托管插件市场源：本机源进程内直读发布物，可订阅多个远端源，并向其他实例提供插件发布、审核与分发",
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
