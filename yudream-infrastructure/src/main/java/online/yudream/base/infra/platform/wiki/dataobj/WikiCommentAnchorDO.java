package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;

/**
 * 评论文本锚点持久化对象（评论内嵌子文档，扁平结构）。
 * <p>
 * versionId/revision/contentHash 是服务端盖章结果；blockType 存 lowerCamel 线协议名，
 * 非法/未知类型在读取侧按「丢弃块提示、保留引用文本」处理，不阻断评论读取。
 */
@Data
public class WikiCommentAnchorDO {
    private String exact;
    private String prefix;
    private String suffix;
    private String blockType;
    private Integer blockOrdinal;
    private Integer tableRow;
    private Integer tableCell;
    private Integer startOffset;
    private Integer endOffset;
    private Long versionId;
    private long revision;
    private String contentHash;
}
