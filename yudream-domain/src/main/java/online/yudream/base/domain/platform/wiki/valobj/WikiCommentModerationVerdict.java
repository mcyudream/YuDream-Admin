package online.yudream.base.domain.platform.wiki.valobj;

/**
 * 评论机器审核判定值对象（端口返回，不落库——落库的是评论上的判定枚举与理由摘要）。
 * <p>
 * {@code approved=false} 只表示「不适合直接公开」，由调用方决定回落人工审核还是拒绝落库；
 * 「模型没审成」不属于这个值对象，实现应抛异常，由应用层记成 {@code unavailable}。
 */
public record WikiCommentModerationVerdict(boolean approved, String reason) {

    /** 理由长度上限：只存审计摘要，不存模型长文。 */
    public static final int MAX_REASON_LENGTH = 160;

    public WikiCommentModerationVerdict {
        String normalized = reason == null ? "" : reason.replaceAll("\\s+", " ").trim();
        reason = normalized.length() > MAX_REASON_LENGTH ? normalized.substring(0, MAX_REASON_LENGTH) : normalized;
    }

    public static WikiCommentModerationVerdict approve(String reason) {
        return new WikiCommentModerationVerdict(true, reason);
    }

    public static WikiCommentModerationVerdict reject(String reason) {
        return new WikiCommentModerationVerdict(false, reason);
    }
}
