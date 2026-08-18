package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.agent.service.AgentPluginToolGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.plugin.spi.system.ai.PluginAiToolDescriptor;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
