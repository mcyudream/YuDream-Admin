package online.yudream.base.infra.platform.mcpserver.service;

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

/** MCP 服务器是宿主作为 MCP 服务端对外开放工具的平台能力：完整 MCP 与模块 MCP 两种端点。 */
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.mcp-server", name = "enabled", havingValue = "true")
public class McpServerCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "mcp-server";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "MCP 服务器",
                CapabilityType.AI,
                "宿主作为 MCP（Model Context Protocol）服务端，把插件注册的 AI 工具按权限暴露为 MCP 工具（/api/mcp 完整端点与 /api/mcp/{module} 模块端点）",
                "i-ri:server-line",
                94,
                Map.of(),
                List.of()
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("MCP 服务器能力已启用", Map.of("transport", "streamable-http"))
                : CapabilityHealth.disabled("MCP 服务器能力未启用");
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
                ? CapabilityTestResult.success("MCP 服务器能力可用，端点 /api/mcp（完整）与 /api/mcp/{module}（模块），凭 API Key 调用")
                : CapabilityTestResult.failure("MCP 服务器能力未启用");
    }
}
