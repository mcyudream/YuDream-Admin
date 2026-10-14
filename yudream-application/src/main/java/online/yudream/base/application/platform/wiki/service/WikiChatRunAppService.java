package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 管理端问答仅提交后台运行，HTTP/SSE 订阅不拥有模型任务。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class WikiChatRunAppService {
    private static final TypeReference<Map<String, Object>> ACTIVITY = new TypeReference<>() {};
    private final WikiChatAppService chat;
    private final AiRunAppService runs;
    private final ObjectMapper json;

    public AiRunDTO start(Long userId, Long spaceId, String sessionId, String question,
                          List<AiChatMessage> history, List<String> permissions) {
        String context = sessionId == null || sessionId.isBlank() ? "wiki:" + spaceId : sessionId;
        return runs.start(userId, "wiki-chat", context, emitter -> {
            emitter.input(question);
            try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(userId)) {
                return chat.chatStream(spaceId, userId, context, question, List.of(), permissions,
                        emitter::content, emitter::reasoning, emitter::tool,
                        activity -> emitter.progress(activity.phase(), json.convertValue(activity, ACTIVITY)));
            }
        }, null);
    }
}
