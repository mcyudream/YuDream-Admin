package online.yudream.base.domain.platform.mcpserver.service;

import online.yudream.base.domain.platform.mcpserver.valobj.McpExposedTool;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务器工具来源端口：宿主作为 MCP 服务端对外暴露的工具目录与执行入口。
 * 本期唯一实现是插件工具桥（PluginAiToolRegistry）；宿主原生工具模块后续按需扩展。
 */
public interface McpToolSource {

    /** 有可暴露工具的模块清单（code + 显示名），按 code 排序。 */
    List<McpToolModuleRef> modules();

    /** 模块下按暴露策略过滤后的工具清单（声明 MCP 触发、非 DESTRUCTIVE、不需确认）。 */
    List<McpExposedTool> exposedTools(String module);

    /** 执行模块内工具；权限校验由应用层完成，此处只负责身份上下文组装与调用。 */
    McpToolOutcome call(String module, String toolName, Map<String, Object> arguments, McpToolPrincipal principal);

    /** 模块引用。 */
    record McpToolModuleRef(String code, String displayName) {
    }
}
