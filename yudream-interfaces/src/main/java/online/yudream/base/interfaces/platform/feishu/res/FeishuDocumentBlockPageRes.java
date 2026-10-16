package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文档块分页响应。
 */
@Data
@Builder
public class FeishuDocumentBlockPageRes implements Serializable {
    private String documentId;
    private List<FeishuDocumentBlockRes> items;
    private String nextPageToken;
    private boolean hasMore;
}
