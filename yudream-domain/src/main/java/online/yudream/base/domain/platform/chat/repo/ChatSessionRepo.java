package online.yudream.base.domain.platform.chat.repo;

import online.yudream.base.domain.platform.chat.aggregate.ChatSession;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepo {

    ChatSession save(ChatSession session);

    Optional<ChatSession> findById(Long id);

    List<ChatSession> findByUserId(Long userId);

    void deleteById(Long id);

    /** 删除该用户的全部会话（账号删除级联）。 */
    void deleteByUserId(Long userId);
}
