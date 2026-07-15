package online.yudream.base.domain.platform.agent.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTool extends BaseDomain {

    private String name;
    private String code;
    private String description;
    private AgentToolType type;
    private String inputSchemaJson;
    private String outputExampleJson;
    private String pythonCode;
    private Integer timeoutMillis;
    private String permissionCode;
    private Boolean enabled;

    public static AgentTool python(String name, String code, String pythonCode) {
        AgentTool tool = new AgentTool();
        tool.update(name, code, null, AgentToolType.PYTHON, "{\"type\":\"object\"}", "{}", pythonCode, 10000, null, true);
        return tool;
    }

    public void update(String name, String code, String description, AgentToolType type, String inputSchemaJson,
                       String outputExampleJson, String pythonCode, Integer timeoutMillis, String permissionCode,
                       Boolean enabled) {
        this.name = required(name, "工具名称不能为空");
        this.code = code(code);
        this.description = description;
        this.type = type == null ? AgentToolType.PYTHON : type;
        this.inputSchemaJson = inputSchemaJson == null || inputSchemaJson.isBlank() ? "{\"type\":\"object\"}" : inputSchemaJson;
        this.outputExampleJson = outputExampleJson == null || outputExampleJson.isBlank() ? "{}" : outputExampleJson;
        this.pythonCode = this.type == AgentToolType.PYTHON ? required(pythonCode, "Python 工具代码不能为空") : null;
        this.timeoutMillis = timeoutMillis == null || timeoutMillis <= 0 ? 10000 : Math.min(timeoutMillis, 60000);
        this.permissionCode = permissionCode;
        this.enabled = enabled == null || enabled;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static String code(String value) {
        String normalized = required(value, "工具编码不能为空").toLowerCase();
        if (!normalized.matches("[a-z][a-z0-9_-]{1,63}")) {
            throw new BizException("工具编码仅支持小写字母、数字、下划线和短横线");
        }
        return normalized;
    }
}
