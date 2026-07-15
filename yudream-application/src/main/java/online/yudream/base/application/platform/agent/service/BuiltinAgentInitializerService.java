package online.yudream.base.application.platform.agent.service;

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
        AgentModelDTO model = defaultChatModel();
        List<AgentApplication> created = new ArrayList<>();
        createIfMissing(
                BuiltinAgentCodes.CMS_BUILDER,
                "CMS 页面构建 Agent",
                "用于 CMS 可视化页面生成、局部修改和画布校验",
                "你是 YuDream CMS 页面构建 Agent。优先调用已授权的 CMS 画布工具完成修改，所有修改完成后必须调用 cms.canvas.validate 校验。",
                CMS_TOOLS,
                model
        ).ifPresent(created::add);
        createIfMissing(
                BuiltinAgentCodes.GROUP_CHATBOT,
                "AI 群聊机器人 Agent",
                "用于群聊提及和随机回复，具体人设与上下文由调用方提供",
                "你是 YuDream 群聊机器人。回答简短、友好、准确，遵守调用方提供的群聊上下文和人设。",
                List.of(),
                model
        ).ifPresent(created::add);
        return List.copyOf(created);
    }

    private java.util.Optional<AgentApplication> createIfMissing(
            String code,
            String name,
            String description,
            String systemPrompt,
            List<String> toolCodes,
            AgentModelDTO model
    ) {
        if (applications.findByCode(code).isPresent()) {
            return java.util.Optional.empty();
        }
        AgentApplication application = AgentApplication.create(name, code);
        application.update(
                name,
                code,
                description,
                "i-ri:robot-2-line",
                systemPrompt,
                workflow(model),
                toolCodes,
                AgentApplicationStatus.DRAFT
        );
        if (model != null) {
            application.publish();
        }
        return java.util.Optional.of(applications.save(application));
    }

    private AgentModelDTO defaultChatModel() {
        Map<String, String> config = capabilities.findByCode("ai")
                .map(module -> module.getConfig() == null ? Map.<String, String>of() : module.getConfig())
                .orElseGet(Map::of);
        List<AgentModelDTO> chatModels = models.parse(config).stream()
                .filter(AgentModelDTO::configured)
                .filter(model -> "chat".equalsIgnoreCase(model.kind()))
                .toList();
        return chatModels.stream().filter(AgentModelDTO::defaultModel).findFirst()
                .orElse(chatModels.isEmpty() ? null : chatModels.getFirst());
    }

    private String workflow(AgentModelDTO model) {
        Map<String, Object> modelData = new LinkedHashMap<>();
        modelData.put("kind", "llm");
        modelData.put("title", "大模型");
        modelData.put("providerCode", model == null ? "" : model.providerCode());
        modelData.put("modelCode", model == null ? "" : model.modelCode());
        modelData.put("vision", model != null && model.vision());
        modelData.put("inputVariable", "query");
        modelData.put("outputVariable", "answer");
        Map<String, Object> graph = Map.of(
                "nodes", List.of(
                        node("start", 80, 180, Map.of(
                                "kind", "start", "title", "开始", "outputVariable", "query"
                        )),
                        node("llm", 390, 180, modelData),
                        node("end", 700, 180, Map.of(
                                "kind", "end", "title", "结束", "inputVariable", "answer"
                        ))
                ),
                "edges", List.of(
                        Map.of("id", "start-llm", "source", "start", "target", "llm"),
                        Map.of("id", "llm-end", "source", "llm", "target", "end")
                )
        );
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
}
