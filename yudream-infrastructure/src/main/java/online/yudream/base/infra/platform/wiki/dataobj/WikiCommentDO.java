package online.yudream.base.infra.platform.wiki.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentAuthorType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentRagStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 页面评论（独立集合）。公开查询只取已发布状态，隐藏/删除为软状态保留审计。
 * anchor 为可选内嵌子文档：历史评论无该字段，读取侧保持 null（不高亮）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformWikiComment")
@CompoundIndexes({
        @CompoundIndex(name = "spaceNodeStatusPinned", def = "{'spaceId':1,'nodeId':1,'status':1,'pinned':-1,'createTime':-1}"),
        @CompoundIndex(name = "spaceStatusTime", def = "{'spaceId':1,'status':1,'createTime':-1}"),
        @CompoundIndex(name = "parentTime", def = "{'parentId':1,'createTime':1}")
})
public class WikiCommentDO extends BaseDO {
    private Long spaceId;
    private Long nodeId;
    private Long parentId;
    private WikiCommentAuthorType authorType;
    private Long authorUserId;
    private String authorName;
    private String content;
    /** 文本锚点（服务端定版）；无锚点评论为 null。 */
    private WikiCommentAnchorDO anchor;
    private WikiCommentStatus status;
    private boolean pinned;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private WikiCommentRagStatus ragStatus;
}
