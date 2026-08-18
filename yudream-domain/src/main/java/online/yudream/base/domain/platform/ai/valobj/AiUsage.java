package online.yudream.base.domain.platform.ai.valobj;

public record AiUsage(
        long promptTokens,
        long completionTokens,
        long totalTokens
) {

    public static AiUsage empty() {
        return new AiUsage(0, 0, 0);
    }

    public AiUsage plus(AiUsage other) {
        if (other == null) {
            return this;
        }
        return new AiUsage(
                promptTokens + other.promptTokens,
                completionTokens + other.completionTokens,
                totalTokens + other.totalTokens);
    }
}
