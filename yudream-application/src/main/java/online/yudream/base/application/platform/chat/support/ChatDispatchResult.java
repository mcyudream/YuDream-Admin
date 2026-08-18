package online.yudream.base.application.platform.chat.support;

import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;

import java.util.List;

public record ChatDispatchResult(
        String content,
        String reasoning,
        AiUsage usage,
        List<ChatCitation> citations
) {

    public static ChatDispatchResult of(String content, AiUsage usage) {
        return of(content, "", usage, List.of());
    }

    public static ChatDispatchResult of(String content, AiUsage usage, List<ChatCitation> citations) {
        return of(content, "", usage, citations);
    }

    public static ChatDispatchResult of(String content, String reasoning, AiUsage usage,
                                        List<ChatCitation> citations) {
        return new ChatDispatchResult(
                content == null ? "" : content,
                reasoning == null ? "" : reasoning,
                usage == null ? AiUsage.empty() : usage,
                citations == null ? List.of() : citations);
    }
}
