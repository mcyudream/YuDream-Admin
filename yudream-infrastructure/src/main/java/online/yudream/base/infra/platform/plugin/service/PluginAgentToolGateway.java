package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.service.AgentPluginToolGateway;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.plugin.spi.system.ai.PluginAiExecutionContext;
import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolCall;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolRisk;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 把插件注册表中的工具描述桥接为 Agent 工具目录，供 /agents/tools/system 等列表展示。 */
@Component
public class PluginAgentToolGateway implements AgentPluginToolGateway {

    private final PluginAiToolRegistry registry;

    public PluginAgentToolGateway(PluginAiToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<AiAgentToolDescriptor> pluginTools() {
        return registry.tools().stream()
                .map(tool -> toDescriptor(tool.descriptor()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public AiAgentTool pluginTool(String toolCode) {
        if (toolCode == null || toolCode.isBlank()) {
            return null;
        }
        String code = toolCode.trim();
        return registry.tools().stream()
                .filter(tool -> tool.descriptor() != null && code.equals(tool.descriptor().name()))
                .findFirst()
                .map(PluginAiToolAdapter::new)
                .orElse(null);
    }

    private AiAgentToolDescriptor toDescriptor(PluginAiToolDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        return new AiAgentToolDescriptor(
                descriptor.name(),
                descriptor.title(),
                descriptor.description(),
                descriptor.permissionCode(),
                null, null, null,
                descriptor.inputSchema()
        );
    }

    /** 插件工具的执行适配：调用时取当前插件触发上下文，按风险/许可/触发/权限逐项校验，与模型工具回调同规则。 */
    private static final class PluginAiToolAdapter implements AiAgentTool {
        private final PluginAiTool tool;
        private final AiAgentToolDescriptor descriptor;

        private PluginAiToolAdapter(PluginAiTool tool) {
            this.tool = tool;
            PluginAiToolDescriptor source = tool.descriptor();
            this.descriptor = new AiAgentToolDescriptor(
                    source.name(),
                    source.title(),
                    source.description(),
                    source.permissionCode(),
                    source.title(),
                    "插件工具",
                    source.description(),
                    source.inputSchema() == null ? Map.of() : source.inputSchema()
            );
        }

        @Override
        public AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            PluginAiToolDescriptor source = tool.descriptor();
            PluginAiExecutionContext context = PluginAiToolExecutionScope.current();
            if (context == null) {
                throw new BizException("插件工具仅可在插件触发的会话中调用：" + source.title());
            }
            if (source.risk() != PluginAiToolRisk.READ
                    || !context.allowsTool(source.name())
                    || !source.allowedTriggers().contains(context.trigger())
                    || !context.hasPermission(source.permissionCode())) {
                throw new BizException("当前用户无权调用 AI 工具：" + source.title());
            }
            Map<String, Object> arguments = call == null || call.arguments() == null ? Map.of() : call.arguments();
            var value = tool.execute(context, new PluginAiToolCall(source.name(), arguments));
            return new AiAgentToolResult(source.name(), value.action(), source.permissionCode(), value.message(), value.payload());
        }
    }
}
