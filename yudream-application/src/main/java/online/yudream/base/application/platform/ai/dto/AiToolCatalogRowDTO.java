package online.yudream.base.application.platform.ai.dto;

import java.util.Map;

/**
 * 工具目录行：系统工具 / 插件工具 / MCP 工具 / Python 自定义工具统一投影，
 * 供管理端一张表展示（来源用标签区分），并承载测试调用所需的输入 schema。
 */
public record AiToolCatalogRowDTO(
        /** Python 自定义工具的主键（编辑/删除用）；其他来源为 null */
        Long toolId,
        String code,
        String name,
        String description,
        /** system | plugin | mcp | python */
        String source,
        String moduleCode,
        String moduleName,
        boolean moduleEnabled,
        /** 模块是否常驻（不常驻即需要 load_tools 加载） */
        boolean moduleAlwaysOn,
        String risk,
        boolean requiresConfirmation,
        /** 工具自身启用状态：Python 工具的可停用标志；系统/插件/MCP 工具跟随模块与服务开关 */
        boolean enabled,
        boolean editable,
        boolean deletable,
        boolean testable,
        String permissionCode,
        Map<String, Object> inputSchema
) {
    public AiToolCatalogRowDTO {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }

    /** 模块开关与工具状态共同决定“当前可用”。 */
    public boolean available() {
        return enabled && moduleEnabled;
    }
}
