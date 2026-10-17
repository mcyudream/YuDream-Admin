package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 系统工具模块目录：全局开关 + 模块（含工具清单）视图。 */
@Data
@Builder
public class AiToolModuleRes {
    private Boolean systemEnabled;
    private Boolean configEnabled;
    private Boolean onDemand;
    private List<Module> modules;

    @Data
    @Builder
    public static class Module {
        private String code;
        private String name;
        private String description;
        private String icon;
        private String source;
        private Boolean alwaysOn;
        private Boolean enabled;
        private Boolean canDisable;
        private Boolean lazy;
        private Integer toolCount;
        private List<Tool> tools;
    }

    @Data
    @Builder
    public static class Tool {
        private String code;
        private String name;
        private String description;
        private String permissionCode;
        private String risk;
        private String source;
        private Boolean requiresConfirmation;
        private Boolean enabled;
    }
}
