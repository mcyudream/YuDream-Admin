package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 评论审核方式（按知识库配置）。
 * <p>
 * 缺省 {@link #MANUAL}：新评论一律落待审核，等治理端人工处理——存量空间的现状行为。
 * {@link #AI}：提交后立即交给 AI 判定，判定通过直接公开；判定不通过、或审核能力不可用时
 * 仍然回到待审核由人工处理，机器审核不会成为唯一入口。
 */
public enum WikiCommentReviewMode {
    /** 人工审核：先审后发，治理端逐条处理。 */
    MANUAL,
    /** AI 审核：先判定，通过即公开；不通过或不可用回落人工审核。 */
    AI;

    /** 空值一律回落人工审核（旧数据没有该字段，不能因此改变行为）。 */
    public static WikiCommentReviewMode of(WikiCommentReviewMode mode) {
        return mode == null ? MANUAL : mode;
    }
}
