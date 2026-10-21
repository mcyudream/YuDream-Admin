package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.agent.enumerate.AgentToolRisk;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiExternalAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AST 草稿工具，只经 AiRun 原生外部交互执行；不包含 HTML、保存、发布或文件工具。 */
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class CmsStudioClientTools {
    public static final List<String> NAMES = List.of("cms.studio.read", "cms.studio.apply", "cms.studio.validate");
    private final CapabilityAppService capabilities;
    private final Environment environment;
    private final ChatSessionRepo sessions;

    public void ensureEnabled() {
        if (!environment.getProperty("yudream.platform.capabilities.cms.enabled", Boolean.class, false))
            throw new BizException("CMS 能力未在当前项目配置中启用");
        capabilities.ensureEnabled("cms", "CMS");
        capabilities.ensureEnabled("ai", "AI 助手");
    }

    public void ensureAccess(List<String> permissions) {
        ensureEnabled();
        for (String permission : List.of("platform:cms:view", "platform:ai:generate", "platform:chat:use"))
            if (!has(permissions, permission)) throw new BizException("站点设计助手缺少权限：" + permission);
    }

    public String contextKey(Long userId, String sessionId) {
        try {
            var session = sessions.findById(Long.valueOf(sessionId)).orElseThrow(() -> new BizException("设计器会话不存在"));
            session.belongsTo(userId);
            if (session.getScopeType() != ChatScopeType.CONSOLE || !"cms-studio".equalsIgnoreCase(session.getAgentCode()))
                throw new BizException("此会话不是站点设计器专用会话");
            String key = session.getSpaceSlug();
            if (key == null || key.isBlank() || key.length() > 240) throw new BizException("设计器上下文标识无效或超过 240 字符");
            return key;
        } catch (NumberFormatException error) { throw new BizException("设计器会话标识无效"); }
    }

    public List<AiAgentTool> bind(Long userId, String sessionId, List<String> permissions, CmsCanvasClientBridge.Outbound outbound) {
        ensureAccess(permissions);
        String context = contextKey(userId, sessionId);
        List<AiAgentTool> tools = new ArrayList<>();
        tools.add(tool("cms.studio.read", "读取设计器 AST 草稿", "分页读取扁平节点、页面与主题/资产摘要。返回 contextKey/documentId/pageId/revision；修改前必须先读。cursor 从 0 起，limit 最多 100。", permissions, context, outbound));
        if (has(permissions, "platform:cms:edit")) tools.add(tool("cms.studio.apply", "原子修改设计器草稿", "仅修改本地草稿，不保存不发布。command 为结构化 StudioCommand；同轮全部修改放在单个 {type:batch,commands:[...]} 中，一次撤销。必须带最近读取的 expectedRevision，陈旧调用拒绝后重新读取。", permissions, context, outbound));
        tools.add(tool("cms.studio.validate", "校验真实设计器文档", "校验当前真实 AST 文档，不接受模型自报文档或校验结论。完成修改后必须调用并确认 valid=true。", permissions, context, outbound));
        return List.copyOf(tools);
    }

    private AiAgentTool tool(String name, String title, String description, List<String> permissions, String context, CmsCanvasClientBridge.Outbound outbound) {
        boolean write = name.equals("cms.studio.apply");
        String permission = write ? "platform:cms:edit" : "platform:cms:view";
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String key : List.of("contextKey", "documentId", "pageId")) properties.put(key, Map.of("type", "string"));
        List<String> required = new ArrayList<>(properties.keySet());
        if (write) {
            properties.put("expectedRevision", Map.of("type", "string"));
            properties.put("command", Map.of("type", "object", "description", "StudioCommand：set-text/update-node/set-style/insert/remove/move/duplicate/set-theme(含 classes/fonts/keyframes)/set-features/add-page/update-page(含 settings)/remove-page/set-assets/batch；batch 禁止嵌套"));
            required.addAll(List.of("expectedRevision", "command"));
        } else if (name.endsWith("read")) {
            properties.put("cursor", Map.of("type", "integer", "minimum", 0));
            properties.put("limit", Map.of("type", "integer", "minimum", 1, "maximum", 100));
        }
        var descriptor = new AiAgentToolDescriptor(name, title, description, permission, title, "CMS", description, Map.of(),
                Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false));
        return new AiExternalAgentTool() {
            public AiAgentToolDescriptor descriptor() { return descriptor; }
            public AgentToolRisk risk() { return write ? AgentToolRisk.WRITE : AgentToolRisk.READ; }
            public AiAgentToolResult prepare(String requestId, AiAgentToolCall call) {
                ensureAccess(permissions);
                if (!has(permissions, permission)) throw new BizException("设计器工具权限已失效");
                var args = call.arguments() == null ? Map.<String, Object>of() : call.arguments();
                if (!context.equals(args.get("contextKey"))) throw new BizException("设计器上下文已变化，请重新读取当前文档");
                for (String key : required) if (args.get(key) == null || args.get(key) instanceof String text && text.isBlank()) throw new BizException("设计器工具缺少参数：" + key);
                if (args.keySet().stream().anyMatch(key -> !properties.containsKey(key))) throw new BizException("设计器工具包含未授权参数");
                outbound.send(requestId, name, args);
                return new AiAgentToolResult(name, "pending", permission, "等待设计器真实执行结果", Map.of("_toolCallId", requestId, "arguments", args));
            }
        };
    }

    private static boolean has(List<String> permissions, String code) { return permissions != null && (permissions.contains("*") || permissions.contains(code)); }
}
