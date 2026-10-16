package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 评论状态。隐藏/删除均为软状态，可恢复到 {@link #PUBLISHED}。
 */
public enum WikiCommentStatus {
    /** 已提交待审核，公开端不可见。 */
    PENDING,
    /** 已发布，公开端可见。 */
    PUBLISHED,
    /** 已隐藏（软下架），可恢复。 */
    HIDDEN,
    /** 已删除（软删除），保留审计记录，可恢复。 */
    DELETED
}
