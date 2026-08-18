package online.yudream.base.application.platform.chat.support;

import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;

public interface ChatDispatcher {

    ChatScopeType scopeType();

    ChatDispatchResult dispatch(ChatDispatchContext context);
}
