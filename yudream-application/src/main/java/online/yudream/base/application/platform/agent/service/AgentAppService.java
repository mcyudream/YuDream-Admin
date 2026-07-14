package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.assembler.AgentAssembler;
import online.yudream.base.application.platform.agent.cmd.AgentApplicationSaveCmd;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.cmd.AgentToolSaveCmd;
import online.yudream.base.application.platform.agent.dto.AgentApplicationDTO;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.dto.AgentToolDTO;
import online.yudream.base.application.platform.agent.query.AgentPageQuery;
import online.yudream.base.application.platform.agent.query.AgentToolPageQuery;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AgentAppService {
    private static final String AI_CAPABILITY = "ai";
    private final CapabilityAppService capabilityAppService;
    private final AgentApplicationRepo applicationRepo;
    private final AgentToolRepo toolRepo;
    private final RuntimeExecutor runtimeExecutor;
    private final ObjectProvider<AiGenerationGateway> generationGatewayProvider;
    private final ObjectProvider<AiAgentTool> systemToolProvider;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PageResult<AgentApplicationDTO> page(AgentPageQuery query) {
        ensureEnabled();
        PageResult<AgentApplication> result = applicationRepo.page(query.getKeyword(), query.getStatus(), query.getPage(), query.getSize());
        return new PageResult<>(result.getRecords().stream().map(AgentAssembler::toDTO).toList(), result.getTotal(), result.getPage(), result.getSize());
    }

    @Transactional(readOnly = true)
    public AgentApplicationDTO detail(Long id) { ensureEnabled(); return AgentAssembler.toDTO(application(id)); }

    @Transactional
    public AgentApplicationDTO save(AgentApplicationSaveCmd cmd) {
        ensureEnabled();
        AgentApplication application = cmd.getId() == null ? create(cmd) : application(cmd.getId());
        applicationRepo.findByCode(cmd.getCode().trim().toLowerCase()).ifPresent(existing -> { if (!Objects.equals(existing.getId(), application.getId())) throw new BizException("Agent 应用编码已存在"); });
        application.update(cmd.getName(), cmd.getCode(), cmd.getDescription(), cmd.getIcon(), cmd.getSystemPrompt(), cmd.getWorkflowJson(), cmd.getToolCodes(), cmd.getStatus());
        return AgentAssembler.toDTO(applicationRepo.save(application));
    }

    @Transactional
    public void publish(Long id) { ensureEnabled(); AgentApplication value = application(id); value.publish(); applicationRepo.save(value); }

    @Transactional
    public void deleteApplication(Long id) { ensureEnabled(); application(id); applicationRepo.deleteById(id); }

    @Transactional(readOnly = true)
    public PageResult<AgentToolDTO> pageTools(AgentToolPageQuery query) {
        ensureEnabled(); PageResult<AgentTool> result = toolRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(result.getRecords().stream().map(AgentAssembler::toDTO).toList(), result.getTotal(), result.getPage(), result.getSize());
    }

    @Transactional(readOnly = true)
    public AgentToolDTO toolDetail(Long id) { ensureEnabled(); return AgentAssembler.toDTO(tool(id)); }

    @Transactional
    public AgentToolDTO saveTool(AgentToolSaveCmd cmd) {
        ensureEnabled();
        if (cmd.getType() == AgentToolType.SYSTEM) throw new BizException("系统工具由平台提供，不能在此修改");
        AgentTool value = cmd.getId() == null ? AgentTool.python(cmd.getName(), cmd.getCode(), cmd.getPythonCode()) : tool(cmd.getId());
        toolRepo.findByCode(cmd.getCode().trim().toLowerCase()).ifPresent(existing -> { if (!Objects.equals(existing.getId(), value.getId())) throw new BizException("工具编码已存在"); });
        value.update(cmd.getName(), cmd.getCode(), cmd.getDescription(), AgentToolType.PYTHON, cmd.getInputSchemaJson(), cmd.getPythonCode(), cmd.getTimeoutMillis(), cmd.getPermissionCode(), cmd.getEnabled());
        return AgentAssembler.toDTO(toolRepo.save(value));
    }

    @Transactional
    public void deleteTool(Long id) { ensureEnabled(); tool(id); toolRepo.deleteById(id); }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> systemTools() {
        ensureEnabled();
        return systemToolProvider.stream().map(tool -> {
            var descriptor = tool.descriptor();
            return Map.<String, Object>of("code", descriptor.name(), "name", descriptor.title(), "description", descriptor.description(), "permissionCode", descriptor.permissionCode(), "inputSchema", descriptor.inputSchema());
        }).toList();
    }

    @Transactional(readOnly = true)
    public AgentRunDTO run(AgentRunCmd cmd) {
        ensureEnabled();
        AgentApplication application = application(cmd.getApplicationId());
        if (application.getStatus() == AgentApplicationStatus.DISABLED) throw new BizException("Agent 应用已停用");
        List<AiAgentToolResult> results = executeWorkflowPythonTools(application, cmd.getInput());
        AiGenerationGateway gateway = generationGatewayProvider.getIfAvailable();
        if (gateway == null) throw new BizException("AI 能力未在当前项目配置中启用");
        String prompt = cmd.getInput() == null ? "" : cmd.getInput();
        if (!results.isEmpty()) prompt += "\n\n工作流工具执行结果：\n" + results;
        String system = (application.getSystemPrompt() == null ? "" : application.getSystemPrompt()) + "\n你正在作为 Agent 应用“" + application.getName() + "”工作。请直接给出对用户有用的结果。";
        var selectedSystemTools = systemToolProvider.stream()
                .map(tool -> tool.descriptor().name())
                .filter(name -> application.getToolCodes() != null && application.getToolCodes().contains(name))
                .collect(java.util.stream.Collectors.toSet());
        AiGenerationRequest request = new AiGenerationRequest(system, prompt, null, cmd.getProviderCode(), cmd.getModelCode(), Map.of())
                .withToolCallingEnabled(!selectedSystemTools.isEmpty());
        var generated = runWithSystemTools(gateway, request, selectedSystemTools);
        return AgentRunDTO.builder().content(generated.summary()).toolResults(results).build();
    }

    private List<AiAgentToolResult> executeWorkflowPythonTools(AgentApplication application, String input) {
        List<String> nodeCodes = pythonToolCodesInWorkflow(application.getWorkflowJson());
        List<AiAgentToolResult> result = new ArrayList<>();
        for (String code : nodeCodes) {
            if (application.getToolCodes() == null || !application.getToolCodes().contains(code)) continue;
            AgentTool tool = toolRepo.findByCode(code).orElseThrow(() -> new BizException("工作流工具不存在：" + code));
            if (tool.getType() != AgentToolType.PYTHON || !Boolean.TRUE.equals(tool.getEnabled())) continue;
            RuntimeScript script = RuntimeScript.create(tool.getName(), tool.getCode(), RuntimeLanguage.PYTHON, tool.getPythonCode());
            script.update(tool.getName(), RuntimeLanguage.PYTHON, tool.getPythonCode(), tool.getTimeoutMillis(), Map.of(), ConnectorStatus.ACTIVE);
            RuntimeExecutionResult execution = runtimeExecutor.execute(script, input);
            result.add(new AiAgentToolResult(tool.getCode(), "python", tool.getPermissionCode(), execution.status().name(), Map.of("stdout", execution.stdout(), "stderr", execution.stderr(), "exitCode", execution.exitCode(), "durationMillis", execution.durationMillis())));
        }
        return result;
    }

    private List<String> pythonToolCodesInWorkflow(String workflowJson) {
        if (workflowJson == null || workflowJson.isBlank()) return List.of();
        try {
            JsonNode nodes = objectMapper.readTree(workflowJson).path("nodes");
            List<String> codes = new ArrayList<>();
            for (JsonNode node : nodes) {
                JsonNode data = node.path("data");
                if ("tool".equals(data.path("kind").asText()) && !data.path("toolCode").asText().isBlank()) codes.add(data.path("toolCode").asText());
            }
            return codes;
        } catch (Exception e) {
            throw new BizException("Agent 工作流格式无效");
        }
    }

    private AgentApplication create(AgentApplicationSaveCmd cmd) {
        String code = cmd.getCode() == null ? null : cmd.getCode().trim().toLowerCase();
        if (code != null && applicationRepo.findByCode(code).isPresent()) throw new BizException("Agent 应用编码已存在");
        return AgentApplication.create(cmd.getName(), cmd.getCode());
    }
    private AgentApplication application(Long id) { return applicationRepo.findById(id).orElseThrow(() -> new BizException("Agent 应用不存在")); }
    private AgentTool tool(Long id) { return toolRepo.findById(id).orElseThrow(() -> new BizException("Agent 工具不存在")); }
    private void ensureEnabled() { capabilityAppService.ensureEnabled(AI_CAPABILITY, "AI Agent"); }

    private online.yudream.base.domain.platform.ai.valobj.AiGenerationResult runWithSystemTools(
            AiGenerationGateway gateway, AiGenerationRequest request, java.util.Set<String> selectedSystemTools
    ) {
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(selectedSystemTools)) {
            return gateway.generate(request);
        }
    }
}
