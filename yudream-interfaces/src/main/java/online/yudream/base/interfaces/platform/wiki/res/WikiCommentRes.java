package online.yudream.base.interfaces.platform.wiki.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论响应。公开端不返回空间/作者/审核人身份字段（null 字段不序列化）；正文按纯文本渲染；
 * 锚点相关字段（anchor/versionId/revision/contentHash/anchorState）在无锚点时全部省略。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WikiCommentRes implements Serializable {
    private String id;
    /** 空间归属（仅治理端返回）。 */
    private String spaceId;
    private String nodeId;
    private String parentId;
    private String authorType;
    /** 登录作者 ID（仅治理端返回，公开端不暴露）。 */
    private String authorUserId;
    private String authorName;
    private String content;
    /** 文本锚点（引用文本 + 定位提示）；无锚点评论（含历史评论）为 null。 */
    private WikiCommentAnchorRes anchor;
    /** 锚点盖章的已发布版本 ID（字符串长 ID）；无锚点时为 null。 */
    private String versionId;
    /** 锚点盖章的发布修订号（字符串长数值）；无锚点时为 null。 */
    private String revision;
    /** 锚点盖章的发布内容哈希；无锚点时为 null。 */
    private String contentHash;
    /** active / reanchorable / orphaned；仅公开读取时返回。 */
    private String anchorState;
    private String status;
    private boolean pinned;
    private String reviewedByUserId;
    private LocalDateTime reviewedAt;
    /** 本条评论实际走的审核方式（MANUAL / AI）；仅治理端返回。 */
    private String reviewMode;
    /** AI 判定（approved / rejected / unavailable）；仅治理端返回。 */
    private String aiVerdict;
    /** AI 判定理由摘要；仅治理端返回。 */
    private String aiNote;
    /** 参与判定的模型（provider/model）；仅治理端返回。 */
    private String aiModel;
    private LocalDateTime aiReviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<WikiCommentRes> replies;
}
