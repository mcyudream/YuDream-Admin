package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 飞书文档纯文本内容：原样透传飞书 raw_content，不经块转换。
 */
@Data
@Builder
public class FeishuDocumentContentDTO {
    private String documentId;
    private String content;
}
