package online.yudream.base.domain.platform.wiki.enumerate;

/**
 * 评论 AI 审核判定结果（落库审计，供治理端与人工复核查看）。
 * <p>
 * 线协议使用小写名称（approved/rejected/unavailable）：{@link #REJECTED} 是「机器认为不该直接公开」，
 * {@link #UNAVAILABLE} 是「机器没审成」（能力未启用、模型未配置、调用或解析失败），
 * 两者都留在待审核，但含义不同，人工复核时要能区分。
 */
public enum WikiCommentAiVerdict {
    /** 机器判定可以公开，评论已自动发布。 */
    APPROVED("approved"),
    /** 机器判定不适合直接公开，留在待审核由人工决定。 */
    REJECTED("rejected"),
    /** 机器没能给出判定，留在待审核由人工决定。 */
    UNAVAILABLE("unavailable");

    private final String wireValue;

    WikiCommentAiVerdict(String wireValue) {
        this.wireValue = wireValue;
    }

    /** 线协议名称（小写）。 */
    public String wireValue() {
        return wireValue;
    }

    /** 解析线协议名或枚举名；未知或空返回 null。 */
    public static WikiCommentAiVerdict of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (WikiCommentAiVerdict verdict : values()) {
            if (verdict.wireValue.equalsIgnoreCase(normalized) || verdict.name().equalsIgnoreCase(normalized)) {
                return verdict;
            }
        }
        return null;
    }
}
