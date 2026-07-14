package online.yudream.base.domain.platform.agent.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AgentApplication extends BaseDomain {

    private String name;
    private String code;
    private String description;
    private String icon;
    private String systemPrompt;
    private String workflowJson;
    private List<String> toolCodes;
    private AgentApplicationStatus status;

    public static AgentApplication create(String name, String code) {
        AgentApplication application = new AgentApplication();
        application.name = required(name, "Agent 应用名称不能为空");
        application.code = code(code);
        application.status = AgentApplicationStatus.DRAFT;
        application.toolCodes = List.of();
        return application;
    }

    public void update(String name, String code, String description, String icon, String systemPrompt,
                       String workflowJson, List<String> toolCodes, AgentApplicationStatus status) {
        this.name = required(name, "Agent 应用名称不能为空");
        this.code = code(code);
        this.description = description;
        this.icon = icon;
        this.systemPrompt = systemPrompt;
        this.workflowJson = required(workflowJson, "请先完成 Agent 工作流编排");
        this.toolCodes = toolCodes == null ? List.of() : toolCodes.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        this.status = status == null ? AgentApplicationStatus.DRAFT : status;
    }

    public void publish() {
        if (workflowJson == null || workflowJson.isBlank()) {
            throw new BizException("请先完成 Agent 工作流编排");
        }
        status = AgentApplicationStatus.PUBLISHED;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private static String code(String value) {
        String normalized = required(value, "Agent 应用编码不能为空").toLowerCase();
        if (!normalized.matches("[a-z][a-z0-9_-]{1,63}")) {
            throw new BizException("Agent 应用编码仅支持小写字母、数字、下划线和短横线");
        }
        return normalized;
    }
}
