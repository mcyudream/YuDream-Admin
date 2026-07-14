package online.yudream.base.infra.platform.agent.service;

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

/** Agent applications are a capability on top of AI generation and the Python integration runtime. */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.agent", name = "enabled", havingValue = "true")
public class AgentCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "agent";
    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "Agent 应用编排",
                CapabilityType.AI,
                "提供可视化工作流编排、Agent 应用运行和受控的系统/Python 工具调用能力",
                "i-ri:robot-2-line",
                94,
                Map.of(),
                List.of("ai", "integration")
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("Agent 应用编排能力已启用", Map.of("runtime", "vue-flow", "pythonTools", true))
                : CapabilityHealth.disabled("Agent 应用编排能力未启用");
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
                ? CapabilityTestResult.success("Agent 应用编排能力可用")
                : CapabilityTestResult.failure("Agent 应用编排能力未启用");
    }
}
