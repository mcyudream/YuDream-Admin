package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.assembler.AgentAssembler;
import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.application.platform.agent.cmd.AgentApplicationSaveCmd;
import online.yudream.base.application.platform.agent.cmd.AgentRunCmd;
import online.yudream.base.application.platform.agent.cmd.AgentToolSaveCmd;
import online.yudream.base.application.platform.agent.dto.AgentApplicationDTO;
import online.yudream.base.application.platform.agent.dto.AgentCatalogDTO;
import online.yudream.base.application.platform.agent.dto.AgentDebugEventDTO;
import online.yudream.base.application.platform.agent.dto.AgentKnowledgeSpaceDTO;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import online.yudream.base.application.platform.agent.dto.AgentRunDTO;
import online.yudream.base.application.platform.agent.dto.AgentToolDTO;
import online.yudream.base.application.platform.agent.query.AgentPageQuery;
import online.yudream.base.application.platform.agent.query.AgentToolPageQuery;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowValidator;
import online.yudream.base.application.platform.agent.workflow.AgentWorkflowToolCodes;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.aggregate.AgentTool;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolType;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.agent.repo.AgentToolRepo;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.platform.agent.service.AgentRuntimeApplicationRegistry;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class AgentAppService {
    private static final String AGENT_CAPABILITY = "agent";
    private static final String AI_CAPABILITY = "ai";
    private static final ObjectMapper TOOL_CONTRACT_MAPPER = new ObjectMapper();

    private final CapabilityAppService capabilityAppService;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final AgentApplicationRepo applicationRepo;
    private final AgentToolRepo toolRepo;
    private final ObjectProvider<AiAgentTool> systemToolProvider;
    private final AgentModelCatalogParser modelCatalogParser;
    private final WikiSpaceRepo wikiSpaceRepo;
    private final AgentWorkflowRuntimeService workflowRuntime;
    private final AgentWorkflowValidator workflowValidator;
    private final AgentPermissionGateway permissionGateway;
    private final AgentRuntimeApplicationRegistry runtimeApplications;

    @Transactional(readOnly = true)
    public PageResult<AgentApplicationDTO> page(AgentPageQuery query) {
        ensureEnabled();
        PageResult<AgentApplication> result = applicationRepo.page(
                query.getKeyword(), query.getStatus(), query.getPage(), query.getSize()
        );
        return new PageResult<>(
                result.getRecords().stream().filter(this::visibleInAgentPage).map(AgentAssembler::toDTO).toList(),
                result.getTotal(),
                result.getPage(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public AgentApplicationDTO detail(Long id) {
        ensureEnabled();
        return AgentAssembler.toDTO(application(id));
    }

    @Transactional(readOnly = true)
    public List<AgentApplicationDTO> publishedApplications() {
        ensureEnabled();
        Map<String, AgentApplication> available = new java.util.LinkedHashMap<>();
        applicationRepo.page(null, AgentApplicationStatus.PUBLISHED, 1, 200).getRecords().stream()
                .filter(this::visibleInAgentPage)
                .filter(application -> !BuiltinAgentCodes.isPluginManaged(application.getCode()) || application.isPluginOverride())
                .forEach(application -> available.put(application.getCode(), application));
        runtimeApplications.applications().forEach(runtime -> {
            java.util.Optional<AgentApplication> override = applicationRepo.findByCode(runtime.getCode())
                    .filter(AgentApplication::isPluginOverride);
            if (override.map(application -> application.getStatus() == AgentApplicationStatus.DISABLED).orElse(false)) {
                available.remove(runtime.getCode());
                return;
            }
            available.put(runtime.getCode(), override
                    .filter(application -> application.getStatus() == AgentApplicationStatus.PUBLISHED)
                    .orElse(runtime));
        });
        return available.values().stream().map(AgentAssembler::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public AgentApplicationDTO runtimeApplication(String code) {
        ensureEnabled();
        return runtimeApplications.findByCode(code)
                .map(AgentAssembler::toDTO)
                .orElseThrow(() -> new BizException("插件 Agent 当前不可用：" + code));
    }

    /** Imports a plugin runtime definition into a local editable overlay. */
    @Transactional
    public AgentApplicationDTO importRuntimeApplication(String code) {
        ensureEnabled();
        String normalizedCode = normalizeCode(code);
        AgentApplication runtime = runtimeApplications.findByCode(normalizedCode)
                .orElseThrow(() -> new BizException("插件 Agent 当前不可用：" + code));
        String pluginCode = runtimeApplications.ownerCode(normalizedCode)
                .orElseThrow(() -> new BizException("无法识别插件 Agent 的归属插件：" + code));
        AgentApplication application = applicationRepo.findByCode(normalizedCode)
                .map(existing -> existingPluginOverride(existing, pluginCode, runtime))
                .orElseGet(() -> applicationRepo.save(AgentApplication.pluginOverride(pluginCode, runtime)));
        return AgentAssembler.toDTO(application);
    }

    @Transactional
    public AgentApplicationDTO save(AgentApplicationSaveCmd cmd) {
        ensureEnabled();
        boolean creating = cmd.getId() == null;
        AgentApplication application = creating ? create(cmd) : application(cmd.getId());
        String normalizedCode = normalizeCode(cmd.getCode());
        if (application.isPluginOverride() && !application.getCode().equals(normalizedCode)) {
            throw new BizException("插件导入的 Agent 不支持修改应用编码");
        }
        applicationRepo.findByCode(normalizedCode).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), application.getId())) {
                throw new BizException("Agent 应用编码已存在");
            }
        });
        AgentWorkflowToolCodes.NormalizedWorkflow workflow = AgentWorkflowToolCodes.normalize(cmd.getWorkflowJson());
        List<String> toolCodes = workflow.toolCodes();
        application.update(
                cmd.getName(), cmd.getCode(), cmd.getDescription(), cmd.getIcon(), cmd.getSystemPrompt(),
                workflow.workflowJson(), toolCodes, savedStatus(application, creating)
        );
        return AgentAssembler.toDTO(applicationRepo.save(application));
    }

    @Transactional
    public void publish(Long id) {
        ensureEnabled();
        AgentApplication value = application(id);
        workflowValidator.validate(value.getWorkflowJson(), validationCatalog(value));
        value.publish();
        applicationRepo.save(value);
    }

    @Transactional
    public void deleteApplication(Long id) {
        ensureEnabled();
        application(id);
        applicationRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PageResult<AgentToolDTO> pageTools(AgentToolPageQuery query) {
        ensureEnabled();
        PageResult<AgentTool> result = toolRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(
                result.getRecords().stream().map(AgentAssembler::toDTO).toList(),
                result.getTotal(),
                result.getPage(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public AgentToolDTO toolDetail(Long id) {
        ensureEnabled();
        return AgentAssembler.toDTO(tool(id));
    }

    @Transactional
    public AgentToolDTO saveTool(AgentToolSaveCmd cmd) {
        ensureEnabled();
        if (cmd.getType() == AgentToolType.SYSTEM) {
            throw new BizException("系统工具由平台提供，不能在此修改");
        }
        validatePythonToolContract(cmd);
        AgentTool value = cmd.getId() == null
                ? AgentTool.python(cmd.getName(), cmd.getCode(), cmd.getPythonCode())
                : tool(cmd.getId());
        toolRepo.findByCode(cmd.getCode().trim().toLowerCase()).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), value.getId())) {
                throw new BizException("工具编码已存在");
            }
        });
        value.update(
                cmd.getName(), cmd.getCode(), cmd.getDescription(), AgentToolType.PYTHON,
                cmd.getInputSchemaJson(), cmd.getOutputExampleJson(), cmd.getPythonCode(), cmd.getTimeoutMillis(),
                cmd.getPermissionCode(), cmd.getEnabled()
        );
        return AgentAssembler.toDTO(toolRepo.save(value));
    }

    private void validatePythonToolContract(AgentToolSaveCmd cmd) {
        JsonNode inputSchema = jsonObject(
                cmd.getInputSchemaJson(),
                "{\"type\":\"object\"}",
                "Python 工具输入 Schema 必须是 JSON 对象"
        );
        if (!"object".equals(inputSchema.path("type").asText())) {
            throw new BizException("Python 工具输入 Schema 的 type 必须为 object");
        }
        jsonObject(
                cmd.getOutputExampleJson(),
                "{}",
                "Python 工具输出格式示例必须是 JSON 对象"
        );
    }

    private JsonNode jsonObject(String source, String fallback, String errorMessage) {
        try {
            JsonNode value = TOOL_CONTRACT_MAPPER.readTree(source == null || source.isBlank() ? fallback : source);
            if (value == null || !value.isObject()) {
                throw new BizException(errorMessage);
            }
            return value;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(errorMessage);
        }
    }

    @Transactional
    public void deleteTool(Long id) {
        ensureEnabled();
        AgentTool value = tool(id);
        List<AgentApplication> applications = applicationRepo.findByToolCode(value.getCode());
        if (!applications.isEmpty()) {
            throw new BizException(
                    "工具仍被 Agent 应用引用，无法删除："
                            + applications.stream().map(AgentApplication::getName)
                            .collect(java.util.stream.Collectors.joining("、"))
            );
        }
        toolRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> systemTools() {
        ensureEnabled();
        return systemToolProvider.stream().map(tool -> {
            var descriptor = tool.descriptor();
            return Map.<String, Object>of(
                    "code", descriptor.name(),
                    "name", descriptor.title(),
                    "description", descriptor.description(),
                    "permissionCode", descriptor.permissionCode(),
                    "inputSchema", descriptor.inputSchema()
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public AgentRunDTO run(AgentRunCmd cmd) {
        ensureEnabled();
        AgentApplication application = publishedApplication(cmd.getApplicationId());
        var result = workflowRuntime.execute(application, cmd, optionalAiConfig(), null, null, null);
        return AgentRunDTO.builder().content(result.content()).toolResults(result.toolResults()).build();
    }

    @Transactional(readOnly = true)
    public AgentRunDTO runByCode(String code, AgentRunCmd cmd) {
        ensureEnabled();
        AgentApplication application = publishedApplication(code);
        var result = workflowRuntime.execute(application, cmd, optionalAiConfig(), null, null, null);
        return AgentRunDTO.builder().content(result.content()).toolResults(result.toolResults()).build();
    }

    @Transactional(readOnly = true)
    public AgentRunDTO debugByCode(
            String code,
            AgentRunCmd cmd,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        ensureEnabled();
        AgentApplication application = publishedApplication(code);
        var result = workflowRuntime.execute(application, cmd, optionalAiConfig(), onNode, onDelta, onTool);
        return AgentRunDTO.builder().content(result.content()).toolResults(result.toolResults()).build();
    }

    @Transactional(readOnly = true)
    public AgentRunDTO debug(
            AgentRunCmd cmd,
            Consumer<AgentDebugEventDTO> onNode,
            Consumer<String> onDelta,
            Consumer<AiAgentToolResult> onTool
    ) {
        ensureEnabled();
        AgentApplication application = debuggableApplication(cmd.getApplicationId());
        var result = workflowRuntime.execute(application, cmd, optionalAiConfig(), onNode, onDelta, onTool);
        return AgentRunDTO.builder().content(result.content()).toolResults(result.toolResults()).build();
    }

    @Transactional(readOnly = true)
    public List<AgentModelDTO> models() {
        ensureEnabled();
        return modelCatalogParser.parse(aiConfig()).stream()
                .filter(model -> "chat".equals(model.kind()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentCatalogDTO catalog() {
        ensureEnabled();
        List<AgentKnowledgeSpaceDTO> spaces = wikiSpaceRepo.findAll().stream()
                .map(space -> new AgentKnowledgeSpaceDTO(
                        space.getSlug(), space.getName(), space.getEmbeddingProviderCode(),
                        space.getEmbeddingModelCode(), space.getTopK(),
                        space.isGraphEnabled(), space.isRerankEnabled()
                ))
                .toList();
        return new AgentCatalogDTO(spaces, modelCatalogParser.parse(optionalAiConfig()));
    }

    private Map<String, String> aiConfig() {
        capabilityAppService.ensureEnabled(AI_CAPABILITY, "AI");
        return capabilityModuleRepo.findByCode(AI_CAPABILITY)
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseThrow(() -> new BizException("AI 能力配置不存在"));
    }

    private Map<String, String> optionalAiConfig() {
        return capabilityModuleRepo.findByCode(AI_CAPABILITY)
                .filter(module -> module.enabled())
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseGet(Map::of);
    }

    private AgentApplication create(AgentApplicationSaveCmd cmd) {
        String code = cmd.getCode() == null ? null : cmd.getCode().trim().toLowerCase();
        if (code != null && applicationRepo.findByCode(code).isPresent()) {
            throw new BizException("Agent 应用编码已存在");
        }
        return AgentApplication.create(cmd.getName(), cmd.getCode());
    }

    private AgentApplication publishedApplication(Long id) {
        AgentApplication application = application(id);
        if (application.getStatus() != AgentApplicationStatus.PUBLISHED) {
            throw new BizException("Agent 应用未发布，正式运行前请先完成发布");
        }
        return application;
    }

    private AgentApplication publishedApplication(String code) {
        String normalizedCode = code == null ? "" : code.trim().toLowerCase();
        java.util.Optional<AgentApplication> runtime = runtimeApplications.findByCode(normalizedCode);
        java.util.Optional<AgentApplication> local = applicationRepo.findByCode(normalizedCode);
        if (runtime.isPresent()) {
            if (local.filter(AgentApplication::isPluginOverride).isPresent()) {
                AgentApplication override = local.get();
                if (override.getStatus() == AgentApplicationStatus.PUBLISHED) {
                    return override;
                }
                if (override.getStatus() == AgentApplicationStatus.DISABLED) {
                    throw new BizException("插件 Agent 的本地覆盖版本已停用：" + override.getName());
                }
            }
            return runtime.get();
        }
        if (local.filter(AgentApplication::isPluginOverride).isPresent()) {
            throw new BizException("所属插件未加载，本地 Agent 覆盖不可用：" + code);
        }
        if (BuiltinAgentCodes.isPluginManaged(normalizedCode)) {
            throw new BizException("插件 Agent 当前不可用：" + code);
        }
        AgentApplication application = local
                .orElseThrow(() -> new BizException("Agent 应用不存在：" + code));
        if (application.getStatus() != AgentApplicationStatus.PUBLISHED) {
            throw new BizException("Agent 应用未发布：" + application.getName());
        }
        return application;
    }

    private AgentApplication existingPluginOverride(AgentApplication existing, String pluginCode, AgentApplication runtime) {
        if (!existing.isPluginOverride() && BuiltinAgentCodes.isPluginManaged(existing.getCode())) {
            existing.adoptPluginOverride(pluginCode, runtime);
            return applicationRepo.save(existing);
        }
        if (!existing.isPluginOverride()) {
            throw new BizException("Agent 编码已被本地应用占用，无法导入插件编排：" + existing.getCode());
        }
        if (!pluginCode.equals(existing.getSourcePluginCode())) {
            throw new BizException("Agent 编码已被其他插件的本地覆盖占用：" + existing.getCode());
        }
        return existing;
    }

    private boolean visibleInAgentPage(AgentApplication application) {
        return !application.isPluginOverride() || runtimeApplications.findByCode(application.getCode()).isPresent();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("Agent 应用编码不能为空");
        }
        return code.trim().toLowerCase();
    }

    private AgentApplication debuggableApplication(Long id) {
        AgentApplication application = application(id);
        if (application.getStatus() == AgentApplicationStatus.DISABLED) {
            throw new BizException("Agent 应用已停用");
        }
        return application;
    }

    private AgentApplicationStatus savedStatus(AgentApplication application, boolean creating) {
        if (creating) {
            return AgentApplicationStatus.DRAFT;
        }
        return application.getStatus() == AgentApplicationStatus.DISABLED
                ? AgentApplicationStatus.DISABLED
                : AgentApplicationStatus.DRAFT;
    }

    private AgentWorkflowValidator.Catalog validationCatalog(AgentApplication application) {
        Set<AgentWorkflowValidator.ModelRef> models = modelCatalogParser.parse(optionalAiConfig()).stream()
                .filter(AgentModelDTO::configured)
                .map(model -> new AgentWorkflowValidator.ModelRef(
                        model.providerCode(), model.modelCode(), model.kind().toLowerCase(), model.vision()
                ))
                .collect(java.util.stream.Collectors.toSet());
        Set<String> knowledgeSpaces = wikiSpaceRepo.findAll().stream()
                .map(space -> space.getSlug())
                .collect(java.util.stream.Collectors.toSet());
        return new AgentWorkflowValidator.Catalog(models, knowledgeSpaces, availableToolCodes(application));
    }

    private Set<String> availableToolCodes(AgentApplication application) {
        Set<String> selected = application.getToolCodes() == null ? Set.of() : Set.copyOf(application.getToolCodes());
        Set<String> result = new java.util.HashSet<>();
        List<AiAgentTool> systemTools = systemToolProvider.stream().toList();
        Set<String> systemToolCodes = systemTools.stream()
                .map(tool -> tool.descriptor().name())
                .collect(java.util.stream.Collectors.toSet());
        systemTools.stream()
                .filter(tool -> selected.contains(tool.descriptor().name()))
                .filter(tool -> permissionGateway.hasPermission(tool.descriptor().permissionCode()))
                .forEach(tool -> result.add(tool.descriptor().name()));
        selected.stream()
                .filter(code -> !systemToolCodes.contains(code))
                .map(toolRepo::findByCode)
                .flatMap(java.util.Optional::stream)
                .filter(tool -> Boolean.TRUE.equals(tool.getEnabled()))
                .filter(tool -> permissionGateway.hasPermission(tool.getPermissionCode()))
                .forEach(tool -> result.add(tool.getCode()));
        return Set.copyOf(result);
    }

    private AgentApplication application(Long id) {
        return applicationRepo.findById(id).orElseThrow(() -> new BizException("Agent 应用不存在"));
    }

    private AgentTool tool(Long id) {
        return toolRepo.findById(id).orElseThrow(() -> new BizException("Agent 工具不存在"));
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(AGENT_CAPABILITY, "Agent 应用编排");
    }
}
