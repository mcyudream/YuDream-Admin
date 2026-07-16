package online.yudream.base.infra.platform.agent.mapper;

import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.infra.platform.agent.dataobj.AgentApplicationDO;
import online.yudream.base.infra.platform.agent.dataobj.AgentToolDO;

public final class AgentInfraMapper {
    private AgentInfraMapper() {}

    public static AgentApplicationDO toDataObj(AgentApplication value) {
        if (value == null) return null;
        AgentApplicationDO data = new AgentApplicationDO();
        data.setId(value.getId()); data.setVersion(value.getVersion()); data.setCreateTime(value.getCreateTime()); data.setUpdateTime(value.getUpdateTime());
        data.setName(value.getName()); data.setCode(value.getCode()); data.setDescription(value.getDescription()); data.setIcon(value.getIcon());
        data.setSystemPrompt(value.getSystemPrompt()); data.setWorkflowJson(value.getWorkflowJson()); data.setToolCodes(value.getToolCodes()); data.setStatus(value.getStatus()); data.setSourcePluginCode(value.getSourcePluginCode());
        return data;
    }

    public static AgentApplication toDomain(AgentApplicationDO value) {
        if (value == null) return null;
        return AgentApplication.builder().id(value.getId()).version(value.getVersion()).createTime(value.getCreateTime()).updateTime(value.getUpdateTime())
                .name(value.getName()).code(value.getCode()).description(value.getDescription()).icon(value.getIcon()).systemPrompt(value.getSystemPrompt())
                .workflowJson(value.getWorkflowJson()).toolCodes(value.getToolCodes()).status(value.getStatus()).sourcePluginCode(value.getSourcePluginCode()).build();
    }

    public static AgentToolDO toDataObj(AgentTool value) {
        if (value == null) return null;
        AgentToolDO data = new AgentToolDO();
        data.setId(value.getId()); data.setVersion(value.getVersion()); data.setCreateTime(value.getCreateTime()); data.setUpdateTime(value.getUpdateTime());
        data.setName(value.getName()); data.setCode(value.getCode()); data.setDescription(value.getDescription()); data.setType(value.getType());
        data.setInputSchemaJson(value.getInputSchemaJson()); data.setOutputExampleJson(value.getOutputExampleJson()); data.setPythonCode(value.getPythonCode()); data.setTimeoutMillis(value.getTimeoutMillis()); data.setPermissionCode(value.getPermissionCode()); data.setEnabled(value.getEnabled());
        return data;
    }

    public static AgentTool toDomain(AgentToolDO value) {
        if (value == null) return null;
        return AgentTool.builder().id(value.getId()).version(value.getVersion()).createTime(value.getCreateTime()).updateTime(value.getUpdateTime())
                .name(value.getName()).code(value.getCode()).description(value.getDescription()).type(value.getType()).inputSchemaJson(value.getInputSchemaJson())
                .outputExampleJson(value.getOutputExampleJson()).pythonCode(value.getPythonCode()).timeoutMillis(value.getTimeoutMillis()).permissionCode(value.getPermissionCode()).enabled(value.getEnabled()).build();
    }
}
