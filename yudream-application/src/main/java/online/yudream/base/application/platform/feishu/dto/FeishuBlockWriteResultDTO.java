package online.yudream.base.application.platform.feishu.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 文档写操作结果：新版本号与受影响块的原始 JSON。
 */
@Data
@Builder
public class FeishuBlockWriteResultDTO {
    private String documentId;
    private String revisionId;
    private List<String> blockIds;
    private List<String> blocksRaw;
}
