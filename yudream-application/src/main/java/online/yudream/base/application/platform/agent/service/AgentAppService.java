package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.assembler.AgentAssembler;
import online.yudream.base.application.platform.agent.cmd.AgentApplicationSaveCmd;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.cmd.AgentToolSaveCmd;
import online.yudream.base.application.platform.agent.dto.AgentApplicationDTO;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
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
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.domain.platform.integration.service.RuntimeExecutor;
import online.yudream.base.domain.platform.integration.valobj.RuntimeExecutionResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentAppService {
    private static final String AGENT_CAPABILITY = "agent";
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
    public void deleteTool(Long id) {
        ensureEnabled();
        AgentTool value = tool(id);
        List<AgentApplication> applications = applicationRepo.findByToolCode(value.getCode());
        if (!applications.isEmpty()) {
            throw new BizException("工具仍被 Agent 应用引用，无法删除：" + applications.stream().map(AgentApplication::getName).collect(java.util.stream.Collectors.joining("、")));
        }
        toolRepo.deleteById(id);
    }

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

    @Transactional(readOnly = true)
    public AgentRunDTO debug(
            AgentRunCmd cmd,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        ensureEnabled();
        AgentApplication application = application(cmd.getApplicationId());
        if (application.getStatus() == AgentApplicationStatus.DISABLED) {
            throw new BizException("Agent 应用已停用");
        }

        List<AiAgentToolResult> workflowToolResults = new ArrayList<>();
        AiGenerationResult generated = null;
        boolean modelExecuted = false;
        for (WorkflowNode node : workflowNodesInExecutionOrder(application.getWorkflowJson())) {
            emitNode(onNode, node, "RUNNING", "正在执行" + node.title());
            try {
                switch (node.kind()) {
                    case "start", "input" -> emitNode(onNode, node, "COMPLETED", "节点执行完成");
                    case "end" -> {
                        if (!modelExecuted) {
                            generated = generateForDebug(application, cmd, workflowToolResults, onDelta, onTool);
                            modelExecuted = true;
                        }
                        emitNode(onNode, node, "COMPLETED", "节点执行完成");
                    }
                    case "tool" -> debugToolNode(application, cmd.getInput(), node, workflowToolResults, onNode, onTool);
                    case "llm" -> {
                        if (modelExecuted) {
                            emitNode(onNode, node, "SKIPPED", "当前调试运行器仅执行首个大模型节点");
                            continue;
                        }
                        generated = generateForDebug(application, cmd, workflowToolResults, onDelta, onTool);
                        modelExecuted = true;
                        emitNode(onNode, node, "COMPLETED", "模型生成完成");
                    }
                    default -> emitNode(onNode, node, "SKIPPED", "当前运行器尚未实现该节点类型");
                }
            } catch (Exception e) {
                emitNode(onNode, node, "FAILED", e.getMessage() == null ? "节点执行失败" : e.getMessage());
                throw e;
            }
        }

        if (!modelExecuted) {
            generated = generateForDebug(application, cmd, workflowToolResults, onDelta, onTool);
        }
        List<AiAgentToolResult> allToolResults = new ArrayList<>(workflowToolResults);
        if (generated.toolResults() != null) {
            allToolResults.addAll(generated.toolResults());
        }
        return AgentRunDTO.builder().content(generated.summary()).toolResults(allToolResults).build();
    }

    private void debugToolNode(
            AgentApplication application,
            String input,
            WorkflowNode node,
            List<AiAgentToolResult> results,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<AiAgentToolResult> onTool
    ) {
        if (node.toolCode() == null || node.toolCode().isBlank()) {
            emitNode(onNode, node, "SKIPPED", "未配置工具");
            return;
        }
        if (application.getToolCodes() == null || !application.getToolCodes().contains(node.toolCode())) {
            throw new BizException("应用未授权工具：" + node.toolCode());
        }
        var customTool = toolRepo.findByCode(node.toolCode());
        if (customTool.isEmpty()) {
            emitNode(onNode, node, "COMPLETED", "系统工具已授权，将由模型按需调用");
            return;
        }
        AgentTool tool = customTool.get();
        if (tool.getType() != AgentToolType.PYTHON || !Boolean.TRUE.equals(tool.getEnabled())) {
            throw new BizException("Python 工具不可用：" + node.toolCode());
        }
        AiAgentToolResult result = executePythonTool(tool, input);
        results.add(result);
        if (onTool != null) {
            onTool.accept(result);
        }
        emitNode(onNode, node, "COMPLETED", "工具执行完成");
    }

    private AiGenerationResult generateForDebug(
            AgentApplication application,
            AgentRunCmd cmd,
            List<AiAgentToolResult> workflowToolResults,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        AiGenerationGateway gateway = generationGatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new BizException("AI 能力未在当前项目配置中启用");
        }
        String prompt = cmd.getInput() == null ? "" : cmd.getInput();
        if (!workflowToolResults.isEmpty()) {
            prompt += "\n\n工作流工具执行结果：\n" + workflowToolResults;
        }
        String system = (application.getSystemPrompt() == null ? "" : application.getSystemPrompt())
                + "\n你正在作为 Agent 应用“" + application.getName() + "”工作。请直接给出对用户有用的结果。";
        Set<String> selectedSystemTools = selectedSystemTools(application);
        AiGenerationRequest request = new AiGenerationRequest(
                system,
                prompt,
                null,
                cmd.getProviderCode(),
                cmd.getModelCode(),
                Map.of()
        ).withToolCallingEnabled(!selectedSystemTools.isEmpty());
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(selectedSystemTools)) {
            return gateway.generateStream(request, onDelta, onTool, null);
        }
    }

    private Set<String> selectedSystemTools(AgentApplication application) {
        return systemToolProvider.stream()
                .map(tool -> tool.descriptor().name())
                .filter(name -> application.getToolCodes() != null && application.getToolCodes().contains(name))
                .collect(java.util.stream.Collectors.toSet());
    }

    private List<AiAgentToolResult> executeWorkflowPythonTools(AgentApplication application, String input) {
        List<String> nodeCodes = pythonToolCodesInWorkflow(application.getWorkflowJson());
        List<AiAgentToolResult> result = new ArrayList<>();
        for (String code : nodeCodes) {
            if (application.getToolCodes() == null || !application.getToolCodes().contains(code)) continue;
            AgentTool tool = toolRepo.findByCode(code).orElseThrow(() -> new BizException("工作流工具不存在：" + code));
            if (tool.getType() != AgentToolType.PYTHON || !Boolean.TRUE.equals(tool.getEnabled())) continue;
            result.add(executePythonTool(tool, input));
        }
        return result;
    }

    private AiAgentToolResult executePythonTool(AgentTool tool, String input) {
        RuntimeScript script = RuntimeScript.create(tool.getName(), tool.getCode(), RuntimeLanguage.PYTHON, tool.getPythonCode());
        script.update(tool.getName(), RuntimeLanguage.PYTHON, tool.getPythonCode(), tool.getTimeoutMillis(), Map.of(), ConnectorStatus.ACTIVE);
        RuntimeExecutionResult execution = runtimeExecutor.execute(script, input);
        return new AiAgentToolResult(
                tool.getCode(),
                "python",
                tool.getPermissionCode(),
                execution.status().name(),
                Map.of(
                        "stdout", execution.stdout(),
                        "stderr", execution.stderr(),
                        "exitCode", execution.exitCode(),
                        "durationMillis", execution.durationMillis()
                )
        );
    }

    private List<WorkflowNode> workflowNodesInExecutionOrder(String workflowJson) {
        if (workflowJson == null || workflowJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(workflowJson);
            Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
            for (JsonNode item : root.path("nodes")) {
                String id = item.path("id").asText();
                if (id.isBlank()) {
                    continue;
                }
                JsonNode data = item.path("data");
                nodes.put(id, new WorkflowNode(
                        id,
                        data.path("kind").asText("unknown"),
                        data.path("title").asText(data.path("label").asText("未命名节点")),
                        data.path("toolCode").asText("")
                ));
            }
            Map<String, Integer> indegree = new HashMap<>();
            Map<String, List<String>> adjacency = new HashMap<>();
            nodes.keySet().forEach(id -> indegree.put(id, 0));
            for (JsonNode item : root.path("edges")) {
                String source = item.path("source").asText();
                String target = item.path("target").asText();
                if (!nodes.containsKey(source) || !nodes.containsKey(target)) {
                    continue;
                }
                adjacency.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
                indegree.computeIfPresent(target, (ignored, value) -> value + 1);
            }
            ArrayDeque<String> queue = new ArrayDeque<>();
            nodes.keySet().stream().filter(id -> indegree.get(id) == 0).forEach(queue::add);
            List<WorkflowNode> ordered = new ArrayList<>();
            while (!queue.isEmpty()) {
                String id = queue.removeFirst();
                ordered.add(nodes.get(id));
                for (String target : adjacency.getOrDefault(id, List.of())) {
                    int remaining = indegree.computeIfPresent(target, (ignored, value) -> value - 1);
                    if (remaining == 0) {
                        queue.addLast(target);
                    }
                }
            }
            nodes.values().stream().filter(node -> !ordered.contains(node)).forEach(ordered::add);
            return ordered;
        } catch (Exception e) {
            throw new BizException("Agent 工作流格式无效");
        }
    }

    private void emitNode(
            Consumer<AgentDebugEventDTO> onNode,
            WorkflowNode node,
            String status,
            String message
    ) {
        if (onNode != null) {
            onNode.accept(new AgentDebugEventDTO(node.id(), node.kind(), node.title(), status, message));
        }
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
    private void ensureEnabled() { capabilityAppService.ensureEnabled(AGENT_CAPABILITY, "Agent 应用编排"); }

    private online.yudream.base.domain.platform.ai.valobj.AiGenerationResult runWithSystemTools(
            AiGenerationGateway gateway, AiGenerationRequest request, java.util.Set<String> selectedSystemTools
    ) {
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(selectedSystemTools)) {
            return gateway.generate(request);
        }
    }

    private record WorkflowNode(String id, String kind, String title, String toolCode) {
    }
}
