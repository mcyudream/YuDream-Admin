package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 飞书文档元信息响应。
 */
@Data
@Builder
public class FeishuDocumentMetaRes implements Serializable {
    private String documentId;
    private String revisionId;
    private String title;
}
