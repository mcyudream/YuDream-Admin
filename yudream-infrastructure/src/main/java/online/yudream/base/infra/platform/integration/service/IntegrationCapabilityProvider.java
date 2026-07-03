package online.yudream.base.infra.platform.integration.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class IntegrationCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "integration";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "集成调用",
                CapabilityType.INTEGRATION,
                "提供 HTTP 调用和 Python 运行脚本能力",
                "i-ri:terminal-box-line",
                70,
                Map.of("pythonCommand", "python")
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("集成调用已启用", Map.of("runtime", "python"))
                : CapabilityHealth.disabled("集成调用未启用");
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
        return enabled.get()
                ? CapabilityTestResult.success("集成调用能力可用")
                : CapabilityTestResult.failure("集成调用未启用");
    }
}
