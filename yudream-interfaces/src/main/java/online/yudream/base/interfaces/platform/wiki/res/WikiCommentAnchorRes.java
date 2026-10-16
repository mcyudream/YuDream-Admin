package online.yudream.base.interfaces.platform.wiki.res;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 评论文本锚点响应（扁平结构）：引用文本与定位提示。
 * <p>
 * 版本身份（versionId/revision/contentHash）与状态（anchorState）与 anchor 平级返回，
 * 客户端可直接映射为锚点 scope 传给纯函数判别组件；blockType 使用 lowerCamel 线协议名。
 */
@Data
@Builder
public class WikiCommentAnchorRes implements Serializable {
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
