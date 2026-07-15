package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.cms.aggregate.CmsBlock;
import online.yudream.base.domain.platform.cms.enumerate.CmsBlockKind;
import online.yudream.base.domain.platform.cms.repo.CmsBlockRepo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class CmsCanvasAtomicAiTools {

    private final CmsBlockRepo cmsBlockRepo;

    @Bean
    public AiAgentTool cmsCanvasSelectedTextTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.text",
                "修改选中元素文案",
                "快速修改当前选中的 GrapesJS 元素文本，不替换整页。",
                "set-selected-text",
                "选中元素文案已更新",
                List.of("selector", "textContent", "htmlContent"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "textContent", "新的纯文本内容",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    @Bean
    public AiAgentTool cmsCanvasSelectedHtmlTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.html",
                "替换选中元素内容",
                "快速替换当前选中 GrapesJS 元素的内部 HTML，不替换整页。",
                "set-selected-html",
                "选中元素内容已更新",
                List.of("selector", "htmlContent", "cssContent"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "htmlContent", "新的内部 HTML 片段",
                        "cssContent", "可选配套 CSS 片段",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    @Bean
    public AiAgentTool cmsCanvasSelectedStyleTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.style",
                "修改选中元素样式",
                "快速修改当前选中 GrapesJS 元素的内联样式，不替换结构。",
                "set-styles",
                "选中元素样式已更新",
                List.of("selector", "styles", "style"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "styles", Map.of("type", "object", "description", "CSS 属性对象，例如 {\"color\":\"#111827\"}"),
                        "style", "可选 CSS 字符串，例如 color:#111827;font-weight:700",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    @Bean
    public AiAgentTool cmsCanvasAddBlockTool() {
        return new FixedCanvasTool(
                "cms.canvas.block.add",
                "追加画布区块",
                "向 GrapesJS 画布末尾追加一个独立 HTML 区块；必须在同一次调用中提供覆盖该区块全部 class 的 CSS。",
                "add-html",
                "新区块已追加到画布",
                List.of("title", "summary", "htmlContent", "cssContent", "jsContent", "markdownContent", "presetCode", "presetId"),
                Map.of(
                        "title", "可选区块标题",
                        "summary", "可选区块摘要",
                        "htmlContent", "要追加到画布末尾的单个区块 HTML",
                        "cssContent", "必填；必须覆盖 htmlContent 中每一个 class 的完整区块 CSS",
                        "jsContent", "该区块需要追加的 JavaScript，不要包含 script 标签",
                        "presetCode", "预设区块编码，传入后优先从 CMS 区块库中读取 htmlContent/cssContent/jsContent",
                        "presetId", "预设区块 ID，辅助字段",
                        "message", "给用户看的简短完成说明"
                ),
                cmsBlockRepo
        );
    }

    @Bean
    public AiAgentTool cmsBlockTemplateListTool() {
        return new BlockTemplateListTool();
    }

    @Bean
    public AiAgentTool cmsCanvasSelectedRemoveTool() {
        return new FixedCanvasTool(
                "cms.canvas.selected.remove",
                "删除选中元素",
                "删除当前选中的 GrapesJS 元素或 selector 命中的第一个元素。",
                "remove-selected",
                "选中元素已删除",
                List.of("selector"),
                Map.of(
                        "selector", "可选 CSS 选择器；为空时作用于当前选中元素",
                        "message", "给用户看的简短完成说明"
                )
        );
    }

    private static final class FixedCanvasTool implements AiAgentTool {

        private final AiAgentToolDescriptor descriptor;
        private final String action;
        private final String defaultMessage;
        private final List<String> payloadKeys;
        private final CmsBlockRepo cmsBlockRepo;

        private FixedCanvasTool(
                String name,
                String title,
                String description,
                String action,
                String defaultMessage,
                List<String> payloadKeys,
                Map<String, Object> inputSchema
        ) {
            this(name, title, description, action, defaultMessage, payloadKeys, inputSchema, null);
        }

        private FixedCanvasTool(
                String name,
                String title,
                String description,
                String action,
                String defaultMessage,
                List<String> payloadKeys,
                Map<String, Object> inputSchema,
                CmsBlockRepo cmsBlockRepo
        ) {
            this.descriptor = new AiAgentToolDescriptor(
                    name,
                    title,
                    description,
                    CmsCanvasAiTool.PERMISSION_CODE,
                    "AI 修改 CMS 画布",
                    "平台能力",
                    "允许 AI Agent 修改 CMS 构建器画布",
                    inputSchema
            );
            this.action = action;
            this.defaultMessage = defaultMessage;
            this.payloadKeys = payloadKeys;
            this.cmsBlockRepo = cmsBlockRepo;
        }

        @Override
        public AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
            final Map<String, Object> finalArgs;
            if ("cms.canvas.block.add".equals(descriptor.name()) && hasArgText(args.get("presetCode"))) {
                finalArgs = resolvePresetBlock(args);
            } else {
                finalArgs = args;
            }
            if ("cms.canvas.block.add".equals(descriptor.name())) {
                CmsCanvasStyleCoverage.requireComplete(
                        finalArgs.get("htmlContent"),
                        finalArgs.get("cssContent"),
                        descriptor.name()
                );
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payloadKeys.forEach(key -> putIfPresent(payload, key, finalArgs.get(key)));
            return new AiAgentToolResult(
                    descriptor.name(),
                    action,
                    CmsCanvasAiTool.PERMISSION_CODE,
                    text(finalArgs.getOrDefault("message", defaultMessage)),
                    payload
            );
        }

        private Map<String, Object> resolvePresetBlock(Map<String, Object> args) {
            String code = String.valueOf(args.get("presetCode")).trim();
            CmsBlock block = cmsBlockRepo.findByCode(code)
                    .filter(b -> Boolean.TRUE.equals(b.getEnabled()))
                    .orElseThrow(() -> new BizException("预设区块不存在或未启用：" + code));
            Map<String, Object> resolved = new LinkedHashMap<>(args);
            resolved.put("title", block.getName());
            resolved.put("htmlContent", block.getHtmlContent());
            resolved.put("cssContent", block.getCssContent());
            resolved.put("jsContent", block.getJsContent());
            return resolved;
        }

        private void putIfPresent(Map<String, Object> target, String key, Object value) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                target.put(key, value);
            }
        }

        private String text(Object value) {
            String text = value == null ? "" : String.valueOf(value).trim();
            return StringUtils.hasText(text) ? text : defaultMessage;
        }

        private boolean hasArgText(Object value) {
            return value != null && StringUtils.hasText(String.valueOf(value).trim());
        }
    }

    private final class BlockTemplateListTool implements AiAgentTool {

        private final AiAgentToolDescriptor descriptor = new AiAgentToolDescriptor(
                "cms.block.template.list",
                "列出可用区块模板",
                "列出 CMS 区块库中已启用的预设区块模板，便于选择合适区块。",
                CmsCanvasAiTool.PERMISSION_CODE,
                "AI 修改 CMS 画布",
                "平台能力",
                "允许 AI Agent 读取 CMS 构建器区块模板",
                Map.of(
                        "category", "可选分类过滤",
                        "kind", "可选区块类型过滤，例如 PRESET"
                )
        );

        @Override
        public AiAgentToolDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public AiAgentToolResult execute(AiAgentToolCall call) {
            Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
            String category = hasArgText(args.get("category")) ? String.valueOf(args.get("category")).trim() : null;
            CmsBlockKind kind = parseKind(args.get("kind"));
            List<CmsBlock> allBlocks = kind != null
                    ? cmsBlockRepo.findEnabledByKind(kind)
                    : cmsBlockRepo.findAllEnabled();
            List<CmsBlock> blocks;
            if (StringUtils.hasText(category)) {
                blocks = allBlocks.stream()
                        .filter(b -> category.equalsIgnoreCase(String.valueOf(b.getCategory())))
                        .toList();
            } else {
                blocks = allBlocks;
            }
            List<Map<String, Object>> templates = blocks.stream()
                    .map(b -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("code", b.getCode());
                        map.put("name", b.getName());
                        map.put("description", b.getDescription());
                        map.put("category", b.getCategory());
                        map.put("kind", b.getKind() == null ? null : b.getKind().name());
                        return map;
                    })
                    .toList();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("templates", templates);
            return new AiAgentToolResult(
                    descriptor.name(),
                    "list-block-templates",
                    CmsCanvasAiTool.PERMISSION_CODE,
                    "已列出可用区块模板",
                    payload
            );
        }

        private CmsBlockKind parseKind(Object value) {
            if (!hasArgText(value)) {
                return null;
            }
            try {
                return CmsBlockKind.valueOf(String.valueOf(value).trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        private boolean hasArgText(Object value) {
            return value != null && StringUtils.hasText(String.valueOf(value).trim());
        }
    }
}
