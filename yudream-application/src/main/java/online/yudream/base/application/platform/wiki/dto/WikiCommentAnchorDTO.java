package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 评论文本锚点视图：引用文本与定位提示（块序号/类型/表格行列、起止偏移），
 * 字段与公开端捕获组件同构。
 * <p>
 * 版本身份（versionId/revision/contentHash）与状态（anchorState）不在锚点对象内，
 * 由评论视图与 anchor 平级下发，客户端可直接映射为锚点 scope。
 */
@Data
@Builder
public class WikiCommentAnchorDTO {
    private String exact;
    private String prefix;
    private String suffix;
    private Integer blockOrdinal;
    private String blockType;
    private Integer tableRow;
    private Integer tableCell;
    private Integer startOffset;
    private Integer endOffset;
}
