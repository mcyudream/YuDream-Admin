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

/**
 * 网站工作台 AI 的客户端工具，只经 AiRun 原生外部交互在设计器面板执行；不包含 HTML、保存、发布或文件工具。
 * 观察（outline/find/inspect/theme/guide/read）低噪声按需读取，修改只有 apply（单个原子 batch），
 * 检查为 validate（文档结构）与 check（手机/桌面渲染检查）。
 */
@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class CmsStudioClientTools {
    public static final List<String> NAMES = List.of("cms.studio.outline", "cms.studio.find", "cms.studio.inspect", "cms.studio.theme",
            "cms.studio.guide", "cms.studio.read", "cms.studio.apply", "cms.studio.validate", "cms.studio.check");
    private static final List<String> REGIONS = List.of("page", "header", "footer", "all");
    private static final List<String> GUIDE_TOPICS = List.of("commands", "styles", "bindings", "content", "effects", "pages", "checklist");
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
        tools.add(tool("cms.studio.outline", "设计器结构概览", "返回当前页面（或页头/页脚、指定子树）的紧凑树：每行 nodeId <tag> .主题样式类 «文本» [data-yd 绑定]，已省略编译生成类与内嵌资源。先用它定位，再 inspect 细节。region 默认 page；rootId 展开子树；depth 1–12，默认 6。",
                permissions, context, outbound, Map.of("region", enumOf(REGIONS), "rootId", Map.of("type", "string"), "depth", Map.of("type", "integer", "minimum", 1, "maximum", 12)), List.of()));
        tools.add(tool("cms.studio.find", "检索设计器节点", "按文本、主题样式类、标签、属性或 data-yd 绑定检索节点（不区分大小写），返回 nodeId、所在区域与祖先路径。field 默认 any，region 默认 all，limit 1–100。",
                permissions, context, outbound, Map.of("query", Map.of("type", "string", "minLength", 1, "maxLength", 120), "field", enumOf(List.of("any", "text", "class", "tag", "attr", "binding")), "region", enumOf(REGIONS), "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)), List.of("query")));
        tools.add(tool("cms.studio.inspect", "查看节点详情", "返回 1–20 个节点的完整字段（属性中的内嵌资源只报长度）、祖先链、直接子节点摘要，以及这些节点用到的主题样式类定义。update-node 前先 inspect 拿原值。",
                permissions, context, outbound, Map.of("nodeIds", Map.of("type", "array", "minItems", 1, "maxItems", 20, "items", Map.of("type", "string"))), List.of("nodeIds")));
        tools.add(tool("cms.studio.theme", "主题与站点数据摘要", "返回主题样式类（含使用次数，可用 query 过滤）、主题变量与字体、站点内容模型（字段类型、effect 与当前值）、导航与数据源、页面设置（外壳页/依赖）。section 默认 all。",
                permissions, context, outbound, Map.of("section", enumOf(List.of("all", "classes", "tokens", "content", "features", "pages")), "query", Map.of("type", "string", "maxLength", 80)), List.of()));
        tools.add(tool("cms.studio.guide", "设计器领域指南", "按主题读取规则细则：commands 修改命令、styles 样式类与响应式、bindings 数据绑定、content 站点内容模型、effects 站点功能、pages 页面与外壳页、checklist 完成前检查。不带 topic 返回目录。",
                permissions, context, outbound, Map.of("topic", enumOf(GUIDE_TOPICS)), List.of()));
        tools.add(tool("cms.studio.read", "读取设计器 AST 草稿", "分页读取当前页面（优先）及共享页头页脚的扁平节点，并返回全部页面与主题/资产摘要（内嵌资源只报长度）。通常先用 outline/find/inspect；需要逐个节点全量时再用。cursor 从 0 起，limit 最多 100。",
                permissions, context, outbound, Map.of("cursor", Map.of("type", "integer", "minimum", 0), "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)), List.of()));
        if (has(permissions, "platform:cms:edit")) tools.add(tool("cms.studio.apply", "原子修改设计器草稿", "仅修改本地草稿，不保存不发布；执行前由用户确认。command 为结构化 StudioCommand，同轮全部修改放在单个 {type:batch,commands:[...]} 中，一次撤销。改内容值用 set-content，增改样式类用 set-class。必须带本轮观察到的 revision 作为 expectedRevision，陈旧调用拒绝后重新观察。命令细则见 guide(commands)。",
                permissions, context, outbound, Map.of("expectedRevision", Map.of("type", "string"), "command", Map.of("type", "object", "description", "StudioCommand：set-text/update-node/set-style/insert/remove/move/duplicate/set-class/set-content/set-theme/set-features/add-page/update-page/remove-page/set-assets/batch；batch 禁止嵌套")), List.of("expectedRevision", "command")));
        tools.add(tool("cms.studio.validate", "校验真实设计器文档", "校验当前真实 AST 文档结构与安全约束，不接受模型自报文档或结论。完成修改后必须调用并确认 valid=true。",
                permissions, context, outbound, Map.of(), List.of()));
        tools.add(tool("cms.studio.check", "渲染检查", "在隐藏 iframe 按手机 390px / 桌面 1440px 渲染当前页面，报告横向溢出、图片失效、文字低对比度、手机过小点击区、文本截断、固定元素遮挡，均带 nodeId。修改后调用；静态渲染不执行数据绑定。viewports 默认两者。",
                permissions, context, outbound, Map.of("viewports", Map.of("type", "array", "minItems", 1, "maxItems", 2, "items", enumOf(List.of("mobile", "desktop")))), List.of()));
        return List.copyOf(tools);
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private AiAgentTool tool(String name, String title, String description, List<String> permissions, String context, CmsCanvasClientBridge.Outbound outbound,
                             Map<String, Object> extra, List<String> extraRequired) {
        boolean write = name.equals("cms.studio.apply");
        String permission = write ? "platform:cms:edit" : "platform:cms:view";
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String key : List.of("contextKey", "documentId", "pageId")) properties.put(key, Map.of("type", "string"));
        List<String> required = new ArrayList<>(properties.keySet());
        properties.putAll(extra);
        required.addAll(extraRequired);
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
