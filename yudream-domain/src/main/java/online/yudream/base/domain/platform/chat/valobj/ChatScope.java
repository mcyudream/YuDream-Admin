package online.yudream.base.domain.platform.chat.valobj;

import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

public record ChatScope(
        ChatScopeType type,
        String agentCode,
        String spaceSlug
) {

    public static ChatScope general() {
        return new ChatScope(ChatScopeType.GENERAL, null, null);
    }

    public static ChatScope agent(String agentCode) {
        return new ChatScope(ChatScopeType.AGENT, agentCode, null);
    }

    public static ChatScope wiki(String spaceSlug) {
        return new ChatScope(ChatScopeType.WIKI, null, spaceSlug);
    }
}
