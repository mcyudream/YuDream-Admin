package online.yudream.base.plugin.spi.system.feishu;

import java.util.List;

/**
 * 飞书文档块（docx blocks，只读视图）。
 *
 * <p>保真约束：{@code rawJson} 是飞书返回的原始块 JSON 原文，宿主不做字段级建模或有损改写；
 * 插件可直接把它原样回填到 {@link PluginFeishuService#createBlockChildren} 的 {@code childrenJson}
 * 提交，样式、提及、表格等未建模字段不丢。上游新增字段无需升级 SPI。</p>
 *
 * @param blockId       块 ID
 * @param parentBlockId 父块 ID；文档根为空串
 * @param blockType     块类型码（飞书原样整数，如 1=page、2=text）；未知为 null
 * @param children      子块 ID 列表（飞书原样顺序）
 * @param rawJson       飞书原始块 JSON 原文
 */
public record PluginFeishuDocBlock(
        String blockId,
        String parentBlockId,
        Integer blockType,
        List<String> children,
        String rawJson
) {
    public PluginFeishuDocBlock {
        blockId = blockId == null ? "" : blockId;
        parentBlockId = parentBlockId == null ? "" : parentBlockId;
        children = children == null ? List.of() : List.copyOf(children);
        rawJson = rawJson == null ? "" : rawJson;
    }
}
