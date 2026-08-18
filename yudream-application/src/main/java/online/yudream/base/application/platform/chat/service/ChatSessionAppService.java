package online.yudream.base.application.platform.chat.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.assembler.ChatAssembler;
import online.yudream.base.application.platform.chat.cmd.ChatSessionSaveCmd;
import online.yudream.base.application.platform.chat.dto.ChatMessageDTO;
import online.yudream.base.application.platform.chat.dto.ChatSessionDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatSessionAppService {

    private final CapabilityAppService capabilities;
    private final ChatSessionRepo sessionRepo;
    private final ChatMessageRepo messageRepo;

    @Transactional(readOnly = true)
    public List<ChatSessionDTO> list(Long userId) {
        ensureEnabled();
        requireUser(userId);
        return sessionRepo.findByUserId(userId).stream().map(ChatAssembler::session).toList();
    }

    @Transactional
    public ChatSessionDTO create(Long userId, ChatSessionSaveCmd cmd) {
        ensureEnabled();
        requireUser(userId);
        ChatSession session = ChatSession.create(userId, cmd.getTitle(), cmd.getScopeType());
        applyScope(session, cmd);
        return ChatAssembler.session(sessionRepo.save(session));
    }

    @Transactional
    public ChatSessionDTO update(Long userId, ChatSessionSaveCmd cmd) {
        ensureEnabled();
        requireUser(userId);
        ChatSession session = session(cmd.getId());
        session.belongsTo(userId);
        if (cmd.getTitle() != null) {
            session.rename(cmd.getTitle());
        }
        applyScope(session, cmd);
        if (cmd.getPinned() != null) {
            session.pin(cmd.getPinned());
        }
        return ChatAssembler.session(sessionRepo.save(session));
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        ensureEnabled();
        requireUser(userId);
        ChatSession session = session(sessionId);
        session.belongsTo(userId);
        messageRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteById(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDTO> messages(Long userId, Long sessionId) {
        ensureEnabled();
        requireUser(userId);
        session(sessionId).belongsTo(userId);
        return messageRepo.findBySessionId(sessionId).stream().map(ChatAssembler::message).toList();
    }

    private void applyScope(ChatSession session, ChatSessionSaveCmd cmd) {
        if (cmd.getScopeType() != null) {
            session.setScopeType(cmd.getScopeType());
        }
        session.setAgentCode(cmd.getAgentCode());
        session.setSpaceSlug(cmd.getSpaceSlug());
        session.setProviderCode(cmd.getProviderCode());
        session.setModelCode(cmd.getModelCode());
    }

    private ChatSession session(Long id) {
        return sessionRepo.findById(id).orElseThrow(() -> new BizException("会话不存在"));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BizException("当前用户未登录");
        }
    }

    private void ensureEnabled() {
        capabilities.ensureEnabled("ai", "AI 助手");
    }
}
