package online.yudream.base.domain.platform.wiki.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentAuthorType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentRagStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiCommentStatus;
import online.yudream.base.domain.platform.wiki.valobj.WikiTextAnchor;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 页面评论（独立聚合/集合）。
 * <p>
 * 正文为纯文本，服务端限长；作者归属只接受认证上下文推导的用户 ID；
 * 状态机 PENDING → PUBLISHED，HIDDEN/DELETED 为可恢复软状态；置顶只对已发布评论生效。
 * 评论作为检索来源默认关闭（{@link WikiCommentRagStatus#DISABLED}），隐藏/删除立即失效。
 * <p>
 * 文本锚点（{@link WikiTextAnchor}）只属于根评论且由服务端按已发布版本盖章；
 * 回复只能继承根评论锚点，不能自带另一份锚点；历史无锚点评论保持兼容（anchor 为 null）。
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WikiComment extends BaseDomain {

    /** 评论正文长度上限（纯文本字符数）。 */
    public static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_AUTHOR_NAME_LENGTH = 64;
    private static final String ANONYMOUS_AUTHOR_NAME = "匿名访客";

    private Long spaceId;
    private Long nodeId;
    /** 父评论 ID；为空表示根评论。仅支持两级（同页浅线程）。 */
    private Long parentId;
    private WikiCommentAuthorType authorType;
    /** 登录作者的用户 ID；只由认证上下文推导，匿名评论为 null。 */
    private Long authorUserId;
    private String authorName;
    private String content;
    /** 文本锚点；历史评论与未选文字的评论为 null（保持无高亮展示）。 */
    private WikiTextAnchor anchor;
    private WikiCommentStatus status;
    private boolean pinned;
    private Long reviewedByUserId;
    private LocalDateTime reviewedAt;
    private WikiCommentRagStatus ragStatus;

    public static WikiComment create(Long spaceId, Long nodeId, Long parentId, Long authorUserId, String authorName,
                                     String content) {
        return create(spaceId, nodeId, parentId, authorUserId, authorName, content, null);
    }

    /** 登录评论（根评论可携带服务端已定锚的文本锚点）。 */
    public static WikiComment create(Long spaceId, Long nodeId, Long parentId, Long authorUserId, String authorName,
                                     String content, WikiTextAnchor anchor) {
        if (parentId != null && anchor != null) {
            throw new BizException("回复评论不能自定义锚点，锚点继承根评论");
        }
        return build(spaceId, nodeId, parentId, WikiCommentAuthorType.USER, authorUserId, authorName, content, anchor);
    }

    /**
     * 登录回复：必须指定根评论，锚点只能继承根评论（不采纳客户端提交的锚点）。
     * <p>
     * 后续若开放「重新定锚」，需要同时更新根评论与其全部回复的锚点副本。
     */
    public static WikiComment reply(Long spaceId, Long nodeId, Long parentId, Long authorUserId, String authorName,
                                    String content, WikiTextAnchor rootAnchor) {
        if (parentId == null) {
            throw new BizException("回复评论必须指定根评论");
        }
        return build(spaceId, nodeId, parentId, WikiCommentAuthorType.USER, authorUserId, authorName, content, rootAnchor);
    }

    public static WikiComment createAnonymous(Long spaceId, Long nodeId, Long parentId, String authorName,
                                              String content) {
        return build(spaceId, nodeId, parentId, WikiCommentAuthorType.ANONYMOUS, null, authorName, content, null);
    }

    private static WikiComment build(Long spaceId, Long nodeId, Long parentId, WikiCommentAuthorType authorType,
                                     Long authorUserId, String authorName, String content, WikiTextAnchor anchor) {
        if (spaceId == null) {
            throw new BizException("知识库不能为空");
        }
        if (nodeId == null) {
            throw new BizException("评论页面不能为空");
        }
        if (authorType == null) {
            throw new BizException("评论作者类型不能为空");
        }
        if (authorType == WikiCommentAuthorType.USER && authorUserId == null) {
            throw new BizException("登录评论缺少作者信息");
        }
        if (authorType == WikiCommentAuthorType.ANONYMOUS && authorUserId != null) {
            throw new BizException("匿名评论不能携带用户身份");
        }
        return WikiComment.builder()
                .spaceId(spaceId)
                .nodeId(nodeId)
                .parentId(parentId)
                .authorType(authorType)
                .authorUserId(authorUserId)
                .authorName(normalizeAuthorName(authorName))
                .content(normalizeContent(content))
                .anchor(anchor)
                .status(WikiCommentStatus.PENDING)
                .pinned(false)
                .ragStatus(WikiCommentRagStatus.DISABLED)
                .build();
    }

    /** 审核发布：待审核评论转为公开可见，并成为检索候选。 */
    public void publish(Long reviewerUserId) {
        if (status == WikiCommentStatus.PUBLISHED) {
            return;
        }
        if (status != WikiCommentStatus.PENDING) {
            throw new BizException("只有待审核评论可以发布，请先恢复评论");
        }
        this.status = WikiCommentStatus.PUBLISHED;
        this.ragStatus = WikiCommentRagStatus.PENDING;
        audit(reviewerUserId);
    }

    /** 隐藏（软下架）：立即从公开端与检索候选移除，可恢复。 */
    public void hide(Long reviewerUserId) {
        if (status == WikiCommentStatus.HIDDEN) {
            return;
        }
        if (status == WikiCommentStatus.DELETED) {
            throw new BizException("已删除评论不能直接隐藏");
        }
        this.status = WikiCommentStatus.HIDDEN;
        this.pinned = false;
        this.ragStatus = WikiCommentRagStatus.DISABLED;
        audit(reviewerUserId);
    }

    /** 软删除：保留记录与审计，可恢复。 */
    public void markDeleted(Long reviewerUserId) {
        if (status == WikiCommentStatus.DELETED) {
            return;
        }
        this.status = WikiCommentStatus.DELETED;
        this.pinned = false;
        this.ragStatus = WikiCommentRagStatus.DISABLED;
        audit(reviewerUserId);
    }

    /** 恢复：隐藏或删除的评论回到已发布；隐藏/删除不会真正丢数据。 */
    public void restore(Long reviewerUserId) {
        if (status == WikiCommentStatus.PUBLISHED) {
            return;
        }
        if (status == WikiCommentStatus.PENDING) {
            throw new BizException("待审核评论请执行发布");
        }
        this.status = WikiCommentStatus.PUBLISHED;
        this.ragStatus = WikiCommentRagStatus.PENDING;
        audit(reviewerUserId);
    }

    public void pin(Long operatorUserId) {
        if (status != WikiCommentStatus.PUBLISHED) {
            throw new BizException("只有已发布评论可以置顶");
        }
        this.pinned = true;
        audit(operatorUserId);
    }

    public void unpin(Long operatorUserId) {
        if (!pinned) {
            return;
        }
        this.pinned = false;
        audit(operatorUserId);
    }

    /** 作者本人编辑正文：只有登录作者可编辑，且不能编辑已删除评论。 */
    public void updateContent(Long operatorUserId, String content) {
        if (authorType != WikiCommentAuthorType.USER || authorUserId == null
                || !Objects.equals(authorUserId, operatorUserId)) {
            throw new BizException("只能编辑本人评论");
        }
        if (status == WikiCommentStatus.DELETED) {
            throw new BizException("已删除评论不能编辑");
        }
        this.content = normalizeContent(content);
        setUpdateTime(LocalDateTime.now());
    }

    /** 作者本人删除（软删除）。 */
    public void deleteByAuthor(Long operatorUserId) {
        if (authorType != WikiCommentAuthorType.USER || authorUserId == null
                || !Objects.equals(authorUserId, operatorUserId)) {
            throw new BizException("只能删除本人评论");
        }
        markDeleted(operatorUserId);
    }

    public boolean published() {
        return status == WikiCommentStatus.PUBLISHED;
    }

    /** 公开端可见的根评论/回复判定。 */
    public boolean visibleToPublic() {
        return published();
    }

    private void audit(Long reviewerUserId) {
        this.reviewedByUserId = reviewerUserId;
        this.reviewedAt = LocalDateTime.now();
        setUpdateTime(LocalDateTime.now());
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            throw new BizException("评论内容不能为空");
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '\n' || current == '\t' || !Character.isISOControl(current)) {
                builder.append(current);
            }
        }
        String cleaned = builder.toString().trim();
        if (cleaned.isEmpty()) {
            throw new BizException("评论内容不能为空");
        }
        if (cleaned.length() > MAX_CONTENT_LENGTH) {
            throw new BizException("评论内容不能超过 " + MAX_CONTENT_LENGTH + " 个字符");
        }
        return cleaned;
    }

    private static String normalizeAuthorName(String authorName) {
        String normalized = authorName == null ? "" : authorName.trim();
        if (normalized.isEmpty()) {
            normalized = ANONYMOUS_AUTHOR_NAME;
        }
        return normalized.length() > MAX_AUTHOR_NAME_LENGTH
                ? normalized.substring(0, MAX_AUTHOR_NAME_LENGTH) : normalized;
    }
}
