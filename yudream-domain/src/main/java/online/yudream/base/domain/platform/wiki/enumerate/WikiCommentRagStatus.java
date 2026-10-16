package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 评论参与检索（RAG）的状态。评论作为检索来源默认关闭：
 * 新建评论为 {@link #DISABLED}，只有审核发布后才成为候选（{@link #PENDING}）。
 * 本状态只是数据契约，索引器由后续批次接入。
 */
public enum WikiCommentRagStatus {
    /** 不参与检索（默认）。 */
    DISABLED,
    /** 待索引（已发布/已恢复的候选状态）。 */
    PENDING,
    /** 已进入检索索引。 */
    INDEXED
}
