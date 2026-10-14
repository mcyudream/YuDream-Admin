package online.yudream.base.application.platform.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiRunResultProjector;
import online.yudream.base.domain.platform.ai.aggregate.AiRun;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatToolCall;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChatAiRunResultProjector implements AiRunResultProjector {
    private final ChatMessageRepo messages;
    private final ChatQuotaAppService quotas;
    private final ObjectMapper json;

    public Object project(AiRun run, Object result, boolean waiting) {
        if (!"chat".equals(run.getModule()) || result == null) return result;
        Map<String, Object> value = json.convertValue(result, new com.fasterxml.jackson.core.type.TypeReference<>() { });
        Object id = value.get("assistantMessageId");
        if (id == null) return result;
        var message = messages.findById(Long.valueOf(String.valueOf(id))).orElse(null);
        if (message == null) return result;
        message.belongsTo(run.getUserId());
        var usage = value.get("usage") == null ? AiUsage.empty() : json.convertValue(value.get("usage"), AiUsage.class);
        message.complete(String.valueOf(value.getOrDefault("content", "")), String.valueOf(value.getOrDefault("reasoning", "")), usage);
        if (waiting) message.suspend();
        if (value.get("tools") instanceof List<?>) message.setTools(online.yudream.base.application.platform.chat.assembler.ChatAssembler.projectionTools(value.get("tools"), json, waiting));
        if (value.get("activities") instanceof List<?>) message.setActivities(online.yudream.base.application.platform.chat.assembler.ChatAssembler.projectionActivities(value.get("activities"), json, waiting));
        messages.save(message);
        var quota = quotas.me(run.getUserId());
        value.put("usedTokens", quota.usedTokens()); value.put("limitTokens", quota.limitTokens()); value.put("remainingTokens", quota.remainingTokens());
        return value;
    }
    public void cancel(AiRun run) {
        if (!"chat".equals(run.getModule()) || run.getResult() == null) return;
        Map<String, Object> result = json.convertValue(run.getResult(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
        if (result.get("assistantMessageId") == null) return;
        messages.findById(Long.valueOf(String.valueOf(result.get("assistantMessageId")))).ifPresent(message -> {
            message.belongsTo(run.getUserId()); message.cancel(); messages.save(message);
        });
    }
}
