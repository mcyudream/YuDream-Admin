package online.yudream.base.application.platform.ai.dto;

import java.util.List;

/**
 * 系统工具模块目录（管理端视图）：全局开关 + 模块（含已关闭模块与其工具清单）。
 */
public record AiToolModuleDTO(
        boolean systemEnabled,
        boolean configEnabled,
        boolean onDemand,
        List<Module> modules
) {
    public AiToolModuleDTO {
        modules = modules == null ? List.of() : List.copyOf(modules);
    }

    /**
     * @param alwaysOn 常驻模块（工具始终进入模型视野，无需 load_tools 加载）
     * @param enabled  模块开关（受全局开关影响）
     * @param canDisable 是否允许关闭（工具目录模块不可关闭）
     * @param lazy     是否按需加载：按需模式下的非常驻模块
     */
    public record Module(
            String code,
            String name,
            String description,
            String icon,
            String source,
            boolean alwaysOn,
            boolean enabled,
            boolean canDisable,
            boolean lazy,
            int toolCount,
            List<Tool> tools,
            /** 运行环境不可用的原因（如部署环境没有 Python），可用时为 null */
            String unavailableReason
    ) {
        public Module {
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    public record Tool(
            String code,
            String name,
            String description,
            String permissionCode,
            String risk,
            String source,
            boolean requiresConfirmation,
            boolean enabled
    ) {
    }
}
