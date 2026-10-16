package online.yudream.base.plugin.spi.system.feishu;

import java.util.List;

/**
 * 飞书文档写操作结果：新版本号与受影响块的原始 JSON。
 *
 * @param documentId 文档 ID
 * @param revisionId 写入后的文档版本号（字符串保留精度）；未知为空串
 * @param blockIds   服务端确认创建的块 ID 列表
 * @param blocksJson 受影响块的原始 JSON 原文列表（与 {@link #blockIds} 对应，保真透传）
 */
public record PluginFeishuBlockWriteResult(
        String documentId,
        String revisionId,
        List<String> blockIds,
        List<String> blocksJson
) {
    public PluginFeishuBlockWriteResult {
        documentId = documentId == null ? "" : documentId;
        revisionId = revisionId == null ? "" : revisionId;
        blockIds = blockIds == null ? List.of() : List.copyOf(blockIds);
        blocksJson = blocksJson == null ? List.of() : List.copyOf(blocksJson);
    }
}
