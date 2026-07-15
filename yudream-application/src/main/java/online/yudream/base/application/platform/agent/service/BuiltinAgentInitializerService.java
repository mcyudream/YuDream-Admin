package online.yudream.base.application.platform.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.assembler.AgentModelCatalogParser;
import online.yudream.base.application.platform.agent.dto.AgentModelDTO;
import online.yudream.base.domain.platform.agent.aggregate.AgentApplication;
import online.yudream.base.domain.platform.agent.enumerate.AgentApplicationStatus;
import online.yudream.base.domain.platform.agent.repo.AgentApplicationRepo;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BuiltinAgentInitializerService {
    private static final List<String> CMS_TOOLS = List.of(
            "web.fetch", "cms.ask.user", "cms.canvas.patch", "cms.chrome.style",
            "cms.canvas.selected.text", "cms.canvas.selected.html", "cms.canvas.selected.style",
            "cms.canvas.block.add", "cms.canvas.selected.remove", "cms.block.template.list",
            "cms.canvas.validate"
    );

    private final AgentApplicationRepo applications;
    private final CapabilityModuleRepo capabilities;
    private final AgentModelCatalogParser models;
    private final ObjectMapper objectMapper;

    public List<AgentApplication> initialize() {
        List<AgentModelDTO> chatModels = configuredChatModels();
        AgentModelDTO model = defaultChatModel(chatModels);
        List<AgentApplication> created = new ArrayList<>();
        createIfMissing(
                BuiltinAgentCodes.CMS_BUILDER,
                "CMS 页面构建 Agent",
                "用于 CMS 可视化页面生成、局部修改和画布校验",
                "你是 YuDream CMS 页面构建 Agent。优先调用已授权的 CMS 画布工具完成修改，所有修改完成后必须调用 cms.canvas.validate 校验。",
                CMS_TOOLS,
                cmsWorkflow(model),
                model != null,
                chatModels
        ).ifPresent(created::add);
        createIfMissing(
                BuiltinAgentCodes.AGUI_CARD,
                "AG-UI 卡片生成 Agent",
                "将用户内容整理为仅供 AG-UI 流展示的结构化卡片",
                """
                        你是 AG-UI 卡片生成 Agent。仅输出一个 JSON 对象，不要输出 Markdown 或解释文字。
                        输出格式：
                        {"title":"卡片标题","summary":"摘要","tone":"info|success|warning|danger",
                         "fields":[{"label":"字段名","value":"字段值"}],
                         "actions":[{"label":"按钮文字","action":"open|submit|copy","value":"动作参数"}]}
                        fields 和 actions 必须是数组，没有内容时返回空数组；所有 value 必须是字符串。
                """,
                List.of(),
                aguiWorkflow(model),
                model != null,
                chatModels
        ).ifPresent(created::add);
        return List.copyOf(created);
    }

    private java.util.Optional<AgentApplication> createIfMissing(
            String code,
            String name,
            String description,
            String systemPrompt,
            List<String> toolCodes,
            String workflowJson,
            boolean publish,
            List<AgentModelDTO> chatModels
    ) {
        java.util.Optional<AgentApplication> existing = applications.findByCode(code);
        if (existing.isPresent() && !requiresWorkflowUpgrade(existing.get(), chatModels)) {
            return java.util.Optional.empty();
        }
        AgentApplication application = existing.orElseGet(() -> AgentApplication.create(name, code));
        AgentApplicationStatus status = application.getStatus() == AgentApplicationStatus.DISABLED
                ? AgentApplicationStatus.DISABLED
                : AgentApplicationStatus.DRAFT;
        application.update(
                name,
                code,
                description,
                "i-ri:robot-2-line",
                systemPrompt,
                workflowJson,
                toolCodes,
                status
        );
        if (publish && status != AgentApplicationStatus.DISABLED) {
            application.publish();
        }
        return java.util.Optional.of(applications.save(application));
    }

    private boolean requiresWorkflowUpgrade(
            AgentApplication application,
            List<AgentModelDTO> chatModels
    ) {
        if (application.getWorkflowJson() == null || application.getWorkflowJson().isBlank()) {
            return true;
        }
        try {
            var nodes = objectMapper.readTree(application.getWorkflowJson()).path("nodes");
            if (nodes.size() <= 3) {
                return true;
            }
            if (chatModels == null || chatModels.isEmpty()) {
                return false;
            }
            if (BuiltinAgentCodes.CMS_BUILDER.equals(application.getCode())
                    && requiresCmsModelToolUpgrade(nodes)) {
                return true;
            }
            for (var node : nodes) {
                var data = node.path("data");
                String kind = data.path("kind").asText();
                if ("plan".equals(node.path("id").asText())
                        && "understand".equals(kind)
                        && !data.has("strictJson")) {
                    return true;
                }
                if (!List.of("llm", "understand", "extract", "classify", "vision").contains(kind)) {
                    continue;
                }
                String providerCode = data.path("providerCode").asText();
                String modelCode = data.path("modelCode").asText();
                boolean valid = chatModels.stream().anyMatch(model ->
                        model.providerCode().equals(providerCode) && model.modelCode().equals(modelCode)
                );
                if (!valid) {
                    return true;
                }
            }
            return false;
        }
        catch (Exception ignored) {
            return true;
        }
    }

    private boolean requiresCmsModelToolUpgrade(JsonNode nodes) {
        JsonNode build = nodeData(nodes, "build");
        if (build == null || !"llm".equals(build.path("kind").asText())) {
            return false;
        }
        if (!build.path("toolConfigDeclared").asBoolean(false)) {
            return true;
        }
        if (!"AUTO".equalsIgnoreCase(build.path("toolMode").asText())) {
            return true;
        }
        if (!Set.copyOf(CMS_TOOLS).equals(Set.copyOf(textValues(build.path("toolCodes"))))) {
            return true;
        }
        return hasToolConfiguration(nodeData(nodes, "plan"))
                || hasToolConfiguration(nodeData(nodes, "clarify"));
    }

    private JsonNode nodeData(JsonNode nodes, String id) {
        for (JsonNode node : nodes) {
            if (id.equals(node.path("id").asText())) {
                return node.path("data");
            }
        }
        return null;
    }

    private boolean hasToolConfiguration(JsonNode data) {
        return data != null && (data.has("toolMode")
                || data.has("toolConfigDeclared")
                || !textValues(data.path("toolCodes")).isEmpty());
    }

    private List<String> textValues(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText().trim());
            }
        });
        return List.copyOf(result);
    }

    private List<AgentModelDTO> configuredChatModels() {
        Map<String, String> config = capabilities.findByCode("ai")
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseGet(Map::of);
        return models.parse(config).stream()
                .filter(AgentModelDTO::configured)
                .filter(model -> "chat".equalsIgnoreCase(model.kind()))
                .toList();
    }

    private AgentModelDTO defaultChatModel(List<AgentModelDTO> chatModels) {
        return chatModels.stream().filter(AgentModelDTO::defaultModel).findFirst()
                .orElse(chatModels.isEmpty() ? null : chatModels.getFirst());
    }

    private String cmsWorkflow(AgentModelDTO model) {
        Map<String, Object> graph = Map.of(
                "nodes", List.of(
                        node("start", 60, 260, Map.of(
                                "kind", "start", "title", "开始", "outputVariable", "query"
                        )),
                        node("plan", 310, 260, tolerantModelNode(
                                "understand", "理解构建需求",
                                "分析 CMS 构建需求，仅输出 JSON：{\"route\":\"clarify|build\",\"goal\":\"目标\",\"constraints\":\"约束\"}。需求足以执行时 route=build，否则 route=clarify。",
                                "query", "plan", model, false
                        )),
                        node("route", 570, 260, Map.of(
                                "kind", "condition", "title", "是否需要澄清",
                                "condition", "plan.route == 'clarify'", "inputVariable", "plan",
                                "outputVariable", "needsClarification"
                        )),
                        node("clarify-task", 820, 90, Map.of(
                                "kind", "template", "title", "组织澄清任务",
                                "template", "目标：{{plan.goal}}\n待确认约束：{{plan.constraints}}\n原始需求：{{query}}",
                                "inputVariable", "plan", "outputVariable", "task"
                        )),
                        node("build-task", 820, 430, Map.of(
                                "kind", "template", "title", "组织构建任务",
                                "template", "执行 CMS 构建目标：{{plan.goal}}\n约束：{{plan.constraints}}\n原始需求：{{query}}",
                                "inputVariable", "plan", "outputVariable", "task"
                        )),
                        node("clarify", 1080, 90, modelNode(
                                "llm", "提出澄清问题", "只提出最多 3 个关键问题，不修改画布。", "task", "answer", model, false
                        )),
                        node("build", 1080, 430, modelNode(
                                "llm", "执行画布构建", "按照任务调用 CMS 画布工具完成修改，并在结束前校验画布。", "task", "answer", model, model != null && model.vision(), CMS_TOOLS
                        )),
                        node("end", 1360, 260, Map.of(
                                "kind", "end", "title", "结束", "inputVariable", "answer"
                        ))
                ),
                "edges", List.of(
                        edge("start-plan", "start", "plan"),
                        edge("plan-route", "plan", "route"),
                        branchEdge("route-clarify", "route", "clarify-task", "true"),
                        branchEdge("route-build", "route", "build-task", "false"),
                        edge("clarify-task-model", "clarify-task", "clarify"),
                        edge("build-task-model", "build-task", "build"),
                        edge("clarify-end", "clarify", "end"),
                        edge("build-end", "build", "end")
                )
        );
        return json(graph);
    }

    private String aguiWorkflow(AgentModelDTO model) {
        Map<String, Object> graph = Map.of(
                "nodes", List.of(
                        node("start", 60, 260, Map.of(
                                "kind", "start", "title", "开始", "outputVariable", "query"
                        )),
                        node("plan", 310, 260, modelNode(
                                "understand", "分析卡片语义",
                                "分析输入并仅输出 JSON：{\"actionable\":true|false,\"title\":\"标题\",\"tone\":\"info|success|warning|danger\",\"focus\":\"重点\"}。",
                                "query", "cardPlan", model, false
                        )),
                        node("route", 570, 260, Map.of(
                                "kind", "condition", "title", "是否包含操作",
                                "condition", "cardPlan.actionable == true", "inputVariable", "cardPlan",
                                "outputVariable", "actionable"
                        )),
                        node("action-task", 820, 90, Map.of(
                                "kind", "template", "title", "组织操作卡片",
                                "template", "生成可操作卡片。标题：{{cardPlan.title}}；语气：{{cardPlan.tone}}；重点：{{cardPlan.focus}}；原文：{{query}}",
                                "inputVariable", "cardPlan", "outputVariable", "cardTask"
                        )),
                        node("summary-task", 820, 430, Map.of(
                                "kind", "template", "title", "组织摘要卡片",
                                "template", "生成只读摘要卡片。标题：{{cardPlan.title}}；语气：{{cardPlan.tone}}；重点：{{cardPlan.focus}}；原文：{{query}}",
                                "inputVariable", "cardPlan", "outputVariable", "cardTask"
                        )),
                        node("action-card", 1080, 90, modelNode(
                                "understand", "生成操作卡片", "严格按系统提示词的 title、summary、tone、fields、actions 格式输出 JSON，actions 只使用 open、submit、copy。", "cardTask", "card", model, false
                        )),
                        node("summary-card", 1080, 430, modelNode(
                                "understand", "生成摘要卡片", "严格按系统提示词的 title、summary、tone、fields、actions 格式输出 JSON，actions 必须为空数组。", "cardTask", "card", model, false
                        )),
                        node("end", 1360, 260, Map.of(
                                "kind", "end", "title", "结束", "inputVariable", "card"
                        ))
                ),
                "edges", List.of(
                        edge("start-plan", "start", "plan"),
                        edge("plan-route", "plan", "route"),
                        branchEdge("route-action", "route", "action-task", "true"),
                        branchEdge("route-summary", "route", "summary-task", "false"),
                        edge("action-task-card", "action-task", "action-card"),
                        edge("summary-task-card", "summary-task", "summary-card"),
                        edge("action-card-end", "action-card", "end"),
                        edge("summary-card-end", "summary-card", "end")
                )
        );
        return json(graph);
    }

    private Map<String, Object> modelNode(
            String kind,
            String title,
            String prompt,
            String inputVariable,
            String outputVariable,
            AgentModelDTO model,
            boolean vision
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", kind);
        data.put("title", title);
        data.put("providerCode", model == null ? "" : model.providerCode());
        data.put("modelCode", model == null ? "" : model.modelCode());
        data.put("vision", vision);
        data.put("prompt", prompt);
        data.put("inputVariable", inputVariable);
        data.put("outputVariable", outputVariable);
        return data;
    }

    private Map<String, Object> modelNode(
            String kind,
            String title,
            String prompt,
            String inputVariable,
            String outputVariable,
            AgentModelDTO model,
            boolean vision,
            List<String> toolCodes
    ) {
        Map<String, Object> data = modelNode(kind, title, prompt, inputVariable, outputVariable, model, vision);
        data.put("toolCodes", List.copyOf(toolCodes));
        data.put("toolMode", "AUTO");
        data.put("toolConfigDeclared", true);
        return data;
    }

    private Map<String, Object> tolerantModelNode(
            String kind,
            String title,
            String prompt,
            String inputVariable,
            String outputVariable,
            AgentModelDTO model,
            boolean vision
    ) {
        Map<String, Object> data = modelNode(kind, title, prompt, inputVariable, outputVariable, model, vision);
        data.put("strictJson", false);
        return data;
    }

    private String json(Map<String, Object> graph) {
        try {
            return objectMapper.writeValueAsString(graph);
        }
        catch (Exception exception) {
            throw new IllegalStateException("无法生成内置 Agent 工作流", exception);
        }
    }

    private Map<String, Object> node(String id, int x, int y, Map<String, Object> data) {
        return Map.of("id", id, "type", "agent", "position", Map.of("x", x, "y", y), "data", data);
    }

    private Map<String, Object> edge(String id, String source, String target) {
        return Map.of("id", id, "source", source, "target", target);
    }

    private Map<String, Object> branchEdge(String id, String source, String target, String sourceHandle) {
        return Map.of("id", id, "source", source, "target", target, "sourceHandle", sourceHandle);
    }
}
