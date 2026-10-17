package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 工具目录行（管理端一张表：系统 / 插件 / MCP / Python 工具用来源标签区分）。 */
@Data
@Builder
public class AiToolCatalogRowRes {
    /** Python 自定义工具主键（编辑/删除用） */
    private Long toolId;
    private String code;
    private String name;
    private String description;
    private String source;
    private String moduleCode;
    private String moduleName;
    private Boolean moduleEnabled;
    private Boolean moduleAlwaysOn;
    private String risk;
    private Boolean requiresConfirmation;
    private Boolean enabled;
    private Boolean editable;
    private Boolean deletable;
    private Boolean testable;
    private String permissionCode;
    private Map<String, Object> inputSchema;
}
