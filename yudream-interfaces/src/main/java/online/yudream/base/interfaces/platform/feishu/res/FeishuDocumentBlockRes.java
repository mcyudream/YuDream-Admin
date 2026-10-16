package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 飞书文档块响应：raw 为飞书原始块 JSON，可原样回填追加接口。
 */
@Data
@Builder
public class FeishuDocumentBlockRes implements Serializable {
    private String blockId;
    private String parentId;
    private Integer blockType;
    private List<String> children;
    private String raw;
}
