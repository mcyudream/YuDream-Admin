package online.yudream.base.domain.platform.mcp.service;

import online.yudream.base.domain.platform.ai.service.AiAgentTool;

import java.util.List;

/**
 * MCP 工具桥接端口：把已启用 MCP 服务端缓存的工具清单投影为可注册的 Agent 工具
 * （工具名 mcp.&lt;serverCode&gt;.&lt;toolName&gt;，模块 mcp:&lt;serverCode&gt;）。
 */
public interface McpAgentToolProvider {

    List<AiAgentTool> tools();

    /** 按平台工具名解析单个工具；不存在返回 null。 */
    AiAgentTool tool(String toolCode);
}
