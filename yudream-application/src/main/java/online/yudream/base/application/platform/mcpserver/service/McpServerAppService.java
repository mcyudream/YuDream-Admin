package online.yudream.base.application.platform.mcpserver.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mcpserver.service.McpToolSource;
import online.yudream.base.domain.platform.mcpserver.valobj.McpExposedTool;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolOutcome;
import online.yudream.base.domain.platform.mcpserver.valobj.McpToolPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP 服务器（平台能力 mcp-server）：把已注册的工具模块按权限暴露为 MCP 工具目录，
 * 并代理 tools/call。完整 MCP（module=null）聚合全部模块；模块 MCP 只含指定模块
 * （模块名 = 插件 code，兼容 plugin:&lt;code&gt; 写法）。工具可见与可调用使用同一套
 * 权限判定，用户只能看到、用自己有权限的工具。
 */
// bean 名显式指定：与旧 MCP 客户端的 mcp.service.McpServerAppService 同 simple name，
// 默认派生名会撞 ConflictingBeanDefinitionException（v1.10.0 生产启动崩溃教训）。
@Service("mcpServerExposeAppService")
@RequiredArgsConstructor
public class McpServerAppService {

    public static final String CAPABILITY = "mcp-server";

    private final CapabilityAppService capabilityAppService;
    private final McpToolSource toolSource;

    /** 工具目录：按权限过滤后的工具与模块摘要（instructions 用）。 */
    public record McpCatalog(List<McpExposedTool> tools, List<String> moduleSummaries) {
    }

    public McpCatalog catalog(String module, List<String> permissions) {
        ensureEnabled();
        List<McpToolSource.McpToolModuleRef> modules = resolveModules(module);
        List<McpExposedTool> tools = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (McpToolSource.McpToolModuleRef ref : modules) {
            List<McpExposedTool> exposed = toolSource.exposedTools(ref.code()).stream()
                    .filter(tool -> permitted(tool, permissions))
                    .toList();
            if (exposed.isEmpty()) {
                continue;
            }
            summaries.add(ref.code() + "（" + ref.displayName() + "）：" + exposed.size() + " 个工具");
            for (McpExposedTool tool : exposed) {
                // 完整聚合撞名时先到模块优先；撞名工具仍可在自己的模块端点下使用。
                if (seen.add(tool.name())) {
                    tools.add(tool);
                }
            }
        }
        return new McpCatalog(List.copyOf(tools), List.copyOf(summaries));
    }

    public McpToolOutcome callTool(String module, String toolName, Map<String, Object> arguments,
                                   McpToolPrincipal principal) {
        ensureEnabled();
        if (toolName == null || toolName.isBlank()) {
            throw new BizException("缺少工具名 toolName");
        }
        McpToolPrincipal effective = principal == null
                ? new McpToolPrincipal(null, List.of())
                : principal;
        String owner = resolveOwner(module, toolName, effective);
        if (owner == null) {
            throw new BizException("工具不存在或无权限调用：" + toolName);
        }
        return toolSource.call(owner, toolName, arguments, effective);
    }

    private String resolveOwner(String module, String toolName, McpToolPrincipal principal) {
        for (McpToolSource.McpToolModuleRef ref : resolveModules(module)) {
            for (McpExposedTool tool : toolSource.exposedTools(ref.code())) {
                if (tool.name().equals(toolName) && permitted(tool, principal.permissions())) {
                    return ref.code();
                }
            }
        }
        return null;
    }

    private List<McpToolSource.McpToolModuleRef> resolveModules(String module) {
        List<McpToolSource.McpToolModuleRef> all = toolSource.modules();
        if (module == null || module.isBlank()) {
            return all;
        }
        String code = module.startsWith("plugin:") ? module.substring("plugin:".length()) : module;
        return all.stream().filter(ref -> ref.code().equals(code)).toList();
    }

    private boolean permitted(McpExposedTool tool, List<String> permissions) {
        return new McpToolPrincipal(null, permissions).hasPermission(tool.permissionCode());
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY, "MCP 服务器");
    }
}
