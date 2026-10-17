package online.yudream.base.application.platform.wiki.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图（治理端与公开端共用）。公开端只返回已发布评论，且不暴露空间/作者与审核人身份字段。
 */
@Data
@Builder
public class WikiCommentDTO {
    private String id;
    private String spaceId;
    private String nodeId;
    private String parentId;
    /** USER / ANONYMOUS */
    private String authorType;
    private String authorUserId;
    private String authorName;
    /** 纯文本正文（输出端按文本渲染，不解析 HTML）。 */
    private String content;
    /** 文本锚点（引用文本 + 定位提示）；为空表示无锚点评论（历史评论保持兼容，不高亮）。 */
    private WikiCommentAnchorDTO anchor;
    /** 锚点盖章的已发布版本 ID（字符串长 ID）；无锚点时为 null。 */
    private String versionId;
    /** 锚点盖章的发布修订号（字符串长数值）；无锚点时为 null。 */
    private String revision;
    /** 锚点盖章的发布内容哈希；无锚点时为 null。 */
    private String contentHash;
    /** active / reanchorable / orphaned；仅公开读取时计算，治理视图为 null。 */
    private String anchorState;
    /** PENDING / PUBLISHED / HIDDEN / DELETED */
    private String status;
    private boolean pinned;
    private String reviewedByUserId;
    private LocalDateTime reviewedAt;
    /** 本条评论实际走的审核方式（MANUAL / AI）；仅治理视图返回。 */
    private String reviewMode;
    /** AI 判定（approved / rejected / unavailable）；仅治理视图返回，未走过 AI 审核为 null。 */
    private String aiVerdict;
    /** AI 判定理由摘要；仅治理视图返回。 */
    private String aiNote;
    /** 参与判定的模型（provider/model）；仅治理视图返回。 */
    private String aiModel;
    private LocalDateTime aiReviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 同页浅线程：根评论的已发布回复（回复不再嵌套）。 */
    private List<WikiCommentDTO> replies;
}
