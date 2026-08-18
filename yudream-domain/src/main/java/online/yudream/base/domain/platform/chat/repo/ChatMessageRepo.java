package online.yudream.base.domain.platform.chat.repo;

import online.yudream.base.domain.platform.chat.aggregate.ChatMessage;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepo {

    ChatMessage save(ChatMessage message);

    Optional<ChatMessage> findById(Long id);

    List<ChatMessage> findBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
