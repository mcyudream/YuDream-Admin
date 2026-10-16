package online.yudream.base.interfaces.platform.feishu.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文档写操作响应：新版本号与受影响块的原始 JSON。
 */
@Data
@Builder
public class FeishuBlockWriteResultRes implements Serializable {
    private String documentId;
    private String revisionId;
    private List<String> blockIds;
    private List<String> blocksRaw;
}
