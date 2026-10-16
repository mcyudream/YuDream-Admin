package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 飞书新版文档元信息。
 */
@Data
@Builder
public class FeishuDocumentMetaDTO {
    private String documentId;
    private String revisionId;
    private String title;
}
