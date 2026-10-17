package online.yudream.base.domain.platform.agent.service;

import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;

import java.util.List;
import java.util.Map;

/**
 * 插件运行时注册的工具目录端口。插件经 SPI registerAiTool 注册的工具不进 Spring 容器，
 * 由 infra 实现从插件工具注册表桥接，供 Agent 工具列表、发布校验与工作流执行读取。
 */
public interface AgentPluginToolGateway {

    List<AiAgentToolDescriptor> pluginTools();

    /** 按工具名解析为可执行的 AiAgentTool（执行时按插件触发上下文做权限与触发校验）；不存在返回 null。 */
    AiAgentTool pluginTool(String toolCode);

    /** 工具名 → 模块编码（plugin:&lt;pluginCode&gt;），供工具模块分组与开关使用。 */
    default Map<String, String> pluginToolModules() {
        return Map.of();
    }
}
