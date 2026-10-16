package online.yudream.base.domain.platform.feishu.valobj;

import java.util.List;

/**
 * 飞书文档写操作结果：新版本号与受影响块的原始 JSON。
 */
public record FeishuBlockWriteResult(
        String documentId,
        String revisionId,
        List<String> blockIds,
        List<String> blocksRaw
) {

    public FeishuBlockWriteResult {
        blockIds = blockIds == null ? List.of() : List.copyOf(blockIds);
        blocksRaw = blocksRaw == null ? List.of() : List.copyOf(blocksRaw);
    }
}
