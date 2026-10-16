package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 飞书文档块：{@code raw} 是飞书返回的原始块 JSON，保真透传。
 * <p>
 * 写回子块时可直接把 {@code raw} 原文提交给追加接口，样式、提及、表格等字段不丢；
 * 不提供「块 -> 文本 -> 块」的有损往返。
 */
@Data
@Builder
public class FeishuDocumentBlockDTO {
    private String blockId;
    private String parentId;
    private Integer blockType;
    private List<String> children;
    /** 飞书原始块 JSON。 */
    private String raw;
}
