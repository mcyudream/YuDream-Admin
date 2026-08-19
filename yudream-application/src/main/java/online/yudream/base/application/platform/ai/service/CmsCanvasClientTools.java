package online.yudream.base.application.platform.ai.service;

import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;

import java.util.List;
import java.util.Map;

/**
 * CMS 画布客户端工具集：描述符在服务端声明，真实执行发生在浏览器 GrapesJS 画布上，
 * 每次调用经 {@link CmsCanvasClientBridge} 挂起等待前端回传真实结果后再返回给模型。
 */
public final class CmsCanvasClientTools {

    public static final String PERMISSION_CODE = "platform:ai:tool:cms-canvas-client";

    private CmsCanvasClientTools() {
    }

    /** 按一次运行绑定桥，产出可直接进入模型工具循环的工具实例。 */
    public static List<AiAgentTool> bind(CmsCanvasClientBridge bridge) {
        return List.of(
                tool(bridge, "cms.canvas.get_outline", "分段读取画布资源", "按资源类型和游标分段读取当前画布。resource=html 返回 HTML 组件纲要，resource=css 返回 CSS 行，resource=js 返回页面 JS 行；每次读取后根据 nextCursor/hasMore 继续调用，动手修改前先读取相关资源", Map.of(
                        "resource", "可选：html（默认）、css、js",
                        "cursor", "可选：分段游标，首次为 0，使用上次返回的 nextCursor",
                        "limit", "可选：html 为节点数、css/js 为行数，默认 60/120",
                        "maxDepth", "可选：HTML 纲要最大深度，默认 6")),
                tool(bridge, "cms.canvas.get_selected", "读取当前选中元素", "读取用户在画布上当前选中元素的 id、标签、类名、样式与文本；未选中时返回空", Map.of()),
                tool(bridge, "cms.canvas.find", "查找组件", "按 CSS 选择器或包含文本查找画布组件，返回匹配的组件 id 列表与摘要", Map.of(
                        "selector", "可选，CSS 选择器，如 .yb-ai-hero、section 等",
                        "text", "可选，按可见文本包含匹配",
                        "limit", "可选，最多返回条数，默认 10")),
                tool(bridge, "cms.canvas.read_component", "读取组件详情", "读取指定组件的外层 HTML（截断）、内联样式、属性与类名，用于修改前确认现状", Map.of(
                        "id", "必填，组件 id，来自纲要或查找结果")),
                tool(bridge, "cms.canvas.update_text", "修改组件文本", "只修改指定组件的可见文本内容，不动结构", Map.of(
                        "id", "必填，组件 id",
                        "text", "必填，新文本")),
                tool(bridge, "cms.canvas.update_html", "替换组件内部 HTML", "替换指定组件的内部 HTML；引入新 class 时必须同时提供 css", Map.of(
                        "id", "必填，组件 id",
                        "html", "必填，新的内部 HTML 片段",
                        "css", "可选但推荐，覆盖本次 html 全部新 class 的 scoped CSS（yb-ai- 前缀）")),
                tool(bridge, "cms.canvas.update_style", "修改组件样式", "以对象形式合并修改指定组件的样式，如 {\"color\":\"#0f172a\",\"margin-top\":\"24px\"}", Map.of(
                        "id", "必填，组件 id",
                        "styles", "必填，样式键值对象")),
                tool(bridge, "cms.canvas.update_attributes", "修改组件属性", "以对象形式合并修改指定组件的属性，如 href、src、data-*；值传 null 表示删除该属性", Map.of(
                        "id", "必填，组件 id",
                        "attributes", "必填，属性键值对象")),
                tool(bridge, "cms.canvas.insert_html", "插入 HTML 区块", "向画布插入一个新区块：可相对某个组件定位，未给 targetId 时追加到页面末尾；css 必须覆盖 html 引入的全部 class", Map.of(
                        "html", "必填，区块 HTML 片段",
                        "css", "必填，覆盖 html 全部 class 的 scoped CSS（yb-ai- 前缀）",
                        "targetId", "可选，定位组件 id；缺省追加到页面主体末尾",
                        "position", "可选，before | after | prepend | append，默认 after（无 targetId 时忽略）")),
                tool(bridge, "cms.canvas.remove_component", "删除组件", "从画布删除指定组件", Map.of(
                        "id", "必填，组件 id")),
                tool(bridge, "cms.canvas.list_blocks", "列出预设区块", "列出区块库中可用的预设区块（id、名称、分类），优先用预设而不是从头写 HTML", Map.of()),
                tool(bridge, "cms.canvas.insert_block", "插入预设区块", "按区块 id 插入预设区块，可相对某组件定位", Map.of(
                        "blockId", "必填，来自 list_blocks 的区块 id",
                        "targetId", "可选，定位组件 id",
                        "position", "可选，before | after | prepend | append，默认 after（无 targetId 时忽略）")),
                tool(bridge, "cms.ask.user", "向用户提问澄清", "需求不明确时向用户提出一个简短问题并给出 2-4 个可点击选项；前端展示选项，用户点选后作为下一条消息继续", Map.of(
                        "question", "必填，要向用户确认的问题",
                        "options", "必填，选项数组，每项含 title 与可选 desc"))
        );
    }

    private static AiAgentTool tool(CmsCanvasClientBridge bridge, String name, String title, String description, Map<String, String> inputs) {
        AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                name, title, description, PERMISSION_CODE, title, "平台能力", description,
                new java.util.LinkedHashMap<>(inputs));
        return new AiAgentTool() {
            @Override
            public AiAgentToolDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public AiAgentToolResult execute(AiAgentToolCall call) {
                Map<String, Object> result = bridge.execute(name, call.arguments());
                Object message = result.get("message");
                return new AiAgentToolResult(
                        name,
                        name.substring(name.lastIndexOf('.') + 1),
                        PERMISSION_CODE,
                        message == null ? title + "完成" : String.valueOf(message),
                        result
                );
            }
        };
    }
}
