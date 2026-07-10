package online.yudream.base.infra.platform.satori.service;

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

/** Project-gated Satori capability; lifecycle changes never open a remote connection. */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriCapabilityProvider implements CapabilityProvider {
    public static final String CODE = "satori";
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(CODE, "Satori", CapabilityType.MESSAGING,
                "Satori v1 多平台消息协议与连接管理能力", "i-ri:chat-3-line", 75,
                Map.of(), List.of());
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("Satori 平台能力已启用", Map.of())
                : CapabilityHealth.disabled("Satori 平台能力未启用");
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
                ? CapabilityTestResult.success("Satori 能力已就绪，请通过连接测试验证远端")
                : CapabilityTestResult.failure("Satori 平台能力未启用");
    }
}
