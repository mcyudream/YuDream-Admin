package online.yudream.base.domain.platform.agent.service;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;

import java.util.List;

/**
 * 插件运行时注册的工具目录端口。插件经 SPI registerAiTool 注册的工具不进 Spring 容器，
 * 由 infra 实现从插件工具注册表桥接，供 Agent 工具列表等应用场景读取。
 */
public interface AgentPluginToolGateway {

    List<AiAgentToolDescriptor> pluginTools();
}
