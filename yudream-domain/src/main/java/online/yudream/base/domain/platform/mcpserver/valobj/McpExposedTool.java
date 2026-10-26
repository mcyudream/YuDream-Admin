package online.yudream.base.domain.platform.mcpserver.valobj;

import java.util.Map;

/**
 * MCP 服务器对外暴露的工具（已按暴露策略过滤：声明 MCP 触发、非 DESTRUCTIVE、不需确认）。
 *
 * @param name           工具名（MCP tools/list 的 name，插件应自带模块前缀防撞名）
 * @param module         所属模块（插件 code）
 * @param title          展示名
 * @param description    描述
 * @param permissionCode 调用所需权限码（空 = 登录即可）
 * @param inputSchema    JSON Schema 形式的入参声明
 * @param write          是否写操作（READ 之外即视为写，调用方须凭授权放行）
 */
public record McpExposedTool(
        String name,
        String module,
        String title,
        String description,
        String permissionCode,
        Map<String, Object> inputSchema,
        boolean write
) {
    public McpExposedTool {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
