package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 文档块分页结果。
 */
@Data
@Builder
public class FeishuDocumentBlockPageDTO {
    private String documentId;
    private List<FeishuDocumentBlockDTO> items;
    private String nextPageToken;
    private boolean hasMore;
}
