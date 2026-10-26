package online.yudream.base.infra.platform.mcpserver.service;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.platform.mcpserver.service.McpToolSource;
import online.yudream.base.domain.platform.mcpserver.valobj.McpExposedTool;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import online.yudream.base.infra.platform.plugin.service.JarPluginRuntimeGateway;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolRegistry;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolCall;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolResult;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 插件工具桥：把 PluginAiToolRegistry 里插件注册的 AI 工具投影为 MCP 工具目录并代理执行。
 * 暴露策略（门控）：工具 descriptor 必须声明 MCP 触发（allowedTriggers 含 "MCP"）、
 * 非 DESTRUCTIVE、不需要二次确认——写操作凭调用者权限码放行。
 * 执行时自建 PluginAiExecutionContext（trigger=MCP，WRITE 工具逐调用点名授权）并挂
 * PluginAiToolExecutionScope（插件侧适配器从该 ThreadLocal 捕获上下文），用后恢复。
 */
@Component
@RequiredArgsConstructor
public class PluginMcpToolBridge implements McpToolSource {

    /** MCP 通道触发标识：工具 descriptor.allowedTriggers 含该值才会经 MCP 暴露。 */
    public static final String TRIGGER = "MCP";

    private final PluginAiToolRegistry registry;
    private final JarPluginRuntimeGateway runtimeGateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<McpToolModuleRef> modules() {
        return registry.toolsByPlugin().keySet().stream()
                .filter(code -> !exposedTools(code).isEmpty())
                .map(code -> new McpToolModuleRef(code, displayName(code)))
                .sorted(Comparator.comparing(McpToolModuleRef::code))
                .toList();
    }

    @Override
    public List<McpExposedTool> exposedTools(String module) {
        return registry.tools(module).stream()
                .map(PluginAiTool::descriptor)
                .filter(descriptor -> descriptor.allowedTriggers().contains(TRIGGER))
                .filter(descriptor -> descriptor.risk() != PluginAiToolRisk.DESTRUCTIVE)
                .filter(descriptor -> !descriptor.requiresConfirmation())
                .map(descriptor -> new McpExposedTool(
                        descriptor.name(), module, descriptor.title(), descriptor.description(),
                        descriptor.permissionCode(), descriptor.inputSchema(),
                        descriptor.risk() == PluginAiToolRisk.WRITE))
                .toList();
    }

    @Override
    public McpToolOutcome call(String module, String toolName, Map<String, Object> arguments,
                               McpToolPrincipal principal) {
        PluginAiTool tool = registry.tools(module).stream()
                .filter(candidate -> candidate.descriptor().name().equals(toolName))
                .findFirst()
                .orElse(null);
        if (tool == null) {
            return new McpToolOutcome(true, "工具不存在或未开放：" + toolName);
        }
        PluginAiToolDescriptor descriptor = tool.descriptor();
        // 写工具逐调用点名授权：调用者能走到这里说明权限码已由应用层复检过。
        List<String> grantedWrites = descriptor.risk() == PluginAiToolRisk.WRITE
                ? List.of(toolName)
                : List.of();
        PluginAiExecutionContext context = new PluginAiExecutionContext(
                principal.userId(), String.valueOf(principal.userId()),
                null, null, null, TRIGGER, null,
                principal.permissions(), List.of("*"), grantedWrites);
        PluginAiExecutionContext previous = PluginAiToolExecutionScope.current();
        PluginAiToolExecutionScope.set(context);
        try {
            PluginAiToolResult result = tool.execute(context, new PluginAiToolCall(toolName, arguments));
            return new McpToolOutcome(false, render(result));
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            return new McpToolOutcome(true, message);
        } finally {
            if (previous == null) {
                PluginAiToolExecutionScope.clear();
            } else {
                PluginAiToolExecutionScope.set(previous);
            }
        }
    }

    private String render(PluginAiToolResult result) {
        StringBuilder text = new StringBuilder(result.message() == null ? "" : result.message());
        if (result.payload() != null && !result.payload().isEmpty()) {
            try {
                String json = objectMapper.writeValueAsString(result.payload());
                text.append(text.isEmpty() ? "" : "\n").append(json);
            } catch (Exception ignored) {
                // payload 序列化失败时保留纯文本结果，不因展示层问题抹掉执行成果
            }
        }
        return text.toString();
    }

    private String displayName(String pluginCode) {
        try {
            return runtimeGateway == null ? pluginCode : runtimeGateway.displayName(pluginCode);
        } catch (RuntimeException ex) {
            return pluginCode;
        }
    }
}
