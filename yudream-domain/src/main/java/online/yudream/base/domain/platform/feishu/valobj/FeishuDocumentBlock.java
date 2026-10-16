package online.yudream.base.domain.platform.feishu.valobj;

import java.util.List;

/**
 * 飞书文档块：结构字段用于定位与遍历，{@code raw} 保留飞书原始块 JSON。
 * <p>
 * 保真约束：块内容不做「块 -> 文本 -> 块」的有损往返。读取侧 {@code raw} 就是飞书返回的块对象，
 * 写回子块时可直接原样提交，保证样式、提及、表格等字段不丢。
 */
public record FeishuDocumentBlock(
        String blockId,
        String parentId,
        Integer blockType,
        List<String> children,
        String raw
) {

    public FeishuDocumentBlock {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
