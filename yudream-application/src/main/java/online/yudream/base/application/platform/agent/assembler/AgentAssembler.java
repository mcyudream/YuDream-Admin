package online.yudream.base.application.platform.agent.assembler;

import online.yudream.base.application.platform.agent.dto.AgentApplicationDTO;
import online.yudream.base.application.platform.agent.dto.AgentToolDTO;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;

public final class AgentAssembler {
    private AgentAssembler() {}
    public static AgentApplicationDTO toDTO(AgentApplication value) {
        if (value == null) return null;
        return AgentApplicationDTO.builder().id(value.getId()).name(value.getName()).code(value.getCode()).description(value.getDescription()).icon(value.getIcon()).systemPrompt(value.getSystemPrompt()).workflowJson(value.getWorkflowJson()).toolCodes(value.getToolCodes()).status(value.getStatus()).createTime(value.getCreateTime()).updateTime(value.getUpdateTime()).build();
    }
    public static AgentToolDTO toDTO(AgentTool value) {
        if (value == null) return null;
        return AgentToolDTO.builder().id(value.getId()).name(value.getName()).code(value.getCode()).description(value.getDescription()).type(value.getType()).inputSchemaJson(value.getInputSchemaJson()).pythonCode(value.getPythonCode()).timeoutMillis(value.getTimeoutMillis()).permissionCode(value.getPermissionCode()).enabled(value.getEnabled()).updateTime(value.getUpdateTime()).build();
    }
}
