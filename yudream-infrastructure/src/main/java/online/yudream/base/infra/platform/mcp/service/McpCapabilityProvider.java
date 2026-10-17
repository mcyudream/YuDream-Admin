package online.yudream.base.infra.platform.mcp.service;

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

/** MCP 服务接入是 AI 之上的平台能力：外部 MCP 服务端的工具桥接为 AI 工具。 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mcp", name = "enabled", havingValue = "true")
public class McpCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "mcp";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "MCP 服务接入",
                CapabilityType.AI,
                "接入外部 MCP（Model Context Protocol）服务端，把其工具桥接为可按模块加载的 AI 工具",
                "i-ri:plug-line",
                93,
                Map.of(),
                List.of("ai")
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("MCP 服务接入能力已启用", Map.of("transport", "streamable-http"))
                : CapabilityHealth.disabled("MCP 服务接入能力未启用");
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
                ? CapabilityTestResult.success("MCP 服务接入能力可用，请在 MCP 服务页面配置服务端地址")
                : CapabilityTestResult.failure("MCP 服务接入能力未启用");
    }
}
