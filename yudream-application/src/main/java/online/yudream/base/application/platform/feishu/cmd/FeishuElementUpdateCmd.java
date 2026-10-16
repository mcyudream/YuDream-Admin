package online.yudream.base.application.platform.feishu.cmd;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 更新文本承载块的文本元素（飞书 update_text_elements 操作）。
 * <p>
 * elements 为文本元素 JSON 原文；本用例不接收整块 PATCH——飞书更新块的请求体是操作载荷，
 * 不是块对象，按块对象提交会造成有损语义。
 */
@Data
@Builder
public class FeishuElementUpdateCmd {
    private String documentId;
    private String blockId;
    private List<String> elements;
    /** 文档版本号；为空按飞书默认 -1（最新版本）处理。 */
    private Long documentRevisionId;
}
