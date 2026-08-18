package online.yudream.base.application.platform.chat.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.chat.cmd.ChatSendCmd;
import online.yudream.base.application.platform.chat.dto.ChatAttachmentDTO;
import online.yudream.base.application.platform.chat.dto.ChatSendResultDTO;
import online.yudream.base.application.platform.chat.support.ChatDispatchContext;
import online.yudream.base.application.platform.chat.support.ChatDispatchResult;
import online.yudream.base.application.platform.chat.support.ChatDispatcher;
import online.yudream.base.application.platform.chat.support.ChatStreamCancelledException;
import online.yudream.base.application.platform.chat.support.ChatWikiContextResolver;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.chat.aggregate.ChatMessage;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;
import online.yudream.base.domain.platform.chat.enumerate.ChatMessageRole;
import online.yudream.base.domain.platform.chat.enumerate.ChatScopeType;
import online.yudream.base.domain.platform.chat.repo.ChatMessageRepo;
import online.yudream.base.domain.platform.chat.repo.ChatSessionRepo;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatAttachment;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import online.yudream.base.domain.platform.chat.valobj.ChatToolCall;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatAppService {

    private static final int HISTORY_LIMIT = 10;

    private final CapabilityAppService capabilities;
    private final ChatSessionRepo sessionRepo;
    private final ChatMessageRepo messageRepo;
    private final ChatQuotaAppService quotaService;
    private final List<ChatDispatcher> dispatchers;
    private final ChatWikiContextResolver wikiContextResolver;

    public ChatSendResultDTO send(Long userId, ChatSendCmd cmd,
                                  Consumer<String> onDelta,
                                  Consumer<AiAgentToolResult> onTool,
                                  Consumer<ChatActivity> onActivity) {
        return send(userId, cmd, onDelta, null, onTool, onActivity);
    }

    public ChatSendResultDTO send(Long userId, ChatSendCmd cmd,
                                  Consumer<String> onDelta,
                                  Consumer<String> onReasoningDelta,
                                  Consumer<AiAgentToolResult> onTool,
                                  Consumer<ChatActivity> onActivity) {
        capabilities.ensureEnabled("ai", "AI 助手");
        requireUser(userId);
        if (cmd == null || !StringUtils.hasText(cmd.getQuestion())) {
            throw new BizException("问题不能为空");
        }
        ChatSession session = sessionFor(userId, cmd);
        ChatScopeType scopeType = cmd.getScopeType() == null ? ChatScopeType.GENERAL : cmd.getScopeType();
        UserChatQuota quota = quotaService.todayQuota(userId);
        if (quota.remaining() <= 0) {
            throw new BizException("今日 token 额度已用完，请明日再试或联系管理员调整额度");
        }
        List<String> permissionCodes = permissionSnapshot(cmd);
        ChatWikiContextResolver.ResolvedContext wikiContext = wikiContextResolver.resolve(
                cmd.getContextRefs(), cmd.getSpaceSlug(), cmd.getQuestion(), permissionCodes);
        List<AiChatMessage> history = buildHistory(session);
        ChatMessage userMessage = persistUserMessage(session, userId, cmd);
        ChatRunState runState = new ChatRunState();
        ChatMessage assistantMessage = persistAssistantMessage(session, userId);
        try {
            ChatDispatchContext context = new ChatDispatchContext(
                    effectiveQuestion(cmd) + wikiContext.prompt(),
                    cmd.getProviderCode(),
                    cmd.getModelCode(),
                    cmd.getAgentCode(),
                    cmd.getSpaceSlug(),
                    cmd.getAttachments() == null ? List.of() : cmd.getAttachments(),
                    history,
                    permissionCodes,
                    onDelta == null ? ignored -> {
                    } : onDelta,
                    delta -> {
                        runState.appendReasoning(delta);
                        if (delta != null && !delta.isEmpty() && onReasoningDelta != null) {
                            onReasoningDelta.accept(delta);
                        }
                    },
                    tool -> {
                        runState.addTool(tool);
                        if (onTool != null) {
                            onTool.accept(tool);
                        }
                    },
                    activity -> {
                        runState.upsertActivity(activity);
                        if (onActivity != null) {
                            onActivity.accept(activity);
                        }
                    });
            ChatDispatchResult result = dispatcher(scopeType).dispatch(context);
            if (!wikiContext.citations().isEmpty()) {
                result = ChatDispatchResult.of(result.content(), result.reasoning(), result.usage(),
                        mergeCitations(result.citations(), wikiContext.citations()));
            }
            ChatRunSnapshot snapshot = runState.snapshot();
            assistantMessage.complete(result.content(), snapshot.reasoning(), result.usage());
            assistantMessage.setCitations(result.citations() == null ? List.of() : result.citations());
            assistantMessage.setTools(settleTools(snapshot.tools(), "complete"));
            assistantMessage.setActivities(settleActivities(snapshot.activities(), "completed"));
            messageRepo.save(assistantMessage);
            quota = quotaService.recordUsage(userId, result.usage().totalTokens());
            session.recordMessage();
            if (!StringUtils.hasText(session.getTitle()) || "新的对话".equals(session.getTitle())) {
                session.rename(truncate(cmd.getQuestion(), 40));
            }
            sessionRepo.save(session);
            return resultOf(session, userMessage, assistantMessage, quota);
        } catch (ChatStreamCancelledException error) {
            throw error;
        } catch (Exception error) {
            ChatRunSnapshot snapshot = runState.snapshot();
            assistantMessage.setReasoning(snapshot.reasoning());
            assistantMessage.fail(error.getMessage());
            assistantMessage.setTools(settleTools(snapshot.tools(), "error"));
            assistantMessage.setActivities(settleActivities(snapshot.activities(), "failed"));
            messageRepo.save(assistantMessage);
            throw error instanceof BizException bizException ? bizException : new BizException(error.getMessage());
        }
    }

    public ChatDispatchResult streamOnce(Long userId, ChatSendCmd cmd,
                                         Consumer<String> onDelta,
                                         Consumer<AiAgentToolResult> onTool,
                                         Consumer<ChatActivity> onActivity) {
        return streamOnce(userId, cmd, onDelta, null, onTool, onActivity);
    }

    public ChatDispatchResult streamOnce(Long userId, ChatSendCmd cmd,
                                         Consumer<String> onDelta,
                                         Consumer<String> onReasoningDelta,
                                         Consumer<AiAgentToolResult> onTool,
                                         Consumer<ChatActivity> onActivity) {
        capabilities.ensureEnabled("ai", "AI 助手");
        requireUser(userId);
        if (cmd == null || !StringUtils.hasText(cmd.getQuestion())) {
            throw new BizException("问题不能为空");
        }
        UserChatQuota quota = quotaService.todayQuota(userId);
        if (quota.remaining() <= 0) {
            throw new BizException("今日 token 额度已用完，请明日再试或联系管理员调整额度");
        }
        List<String> permissionCodes = permissionSnapshot(cmd);
        ChatWikiContextResolver.ResolvedContext wikiContext = wikiContextResolver.resolve(
                cmd.getContextRefs(), cmd.getSpaceSlug(), cmd.getQuestion(), permissionCodes);
        ChatScopeType scopeType = cmd.getScopeType() == null ? ChatScopeType.GENERAL : cmd.getScopeType();
        ChatRunState runState = new ChatRunState();
        ChatDispatchContext context = new ChatDispatchContext(
                effectiveQuestion(cmd) + wikiContext.prompt(),
                cmd.getProviderCode(),
                cmd.getModelCode(),
                cmd.getAgentCode(),
                cmd.getSpaceSlug(),
                cmd.getAttachments() == null ? List.of() : cmd.getAttachments(),
                cmd.getHistory() == null ? List.of() : cmd.getHistory(),
                permissionSnapshot(cmd),
                onDelta == null ? ignored -> {
                } : onDelta,
                delta -> {
                    runState.appendReasoning(delta);
                    if (delta != null && !delta.isEmpty() && onReasoningDelta != null) {
                        onReasoningDelta.accept(delta);
                    }
                },
                tool -> {
                    runState.addTool(tool);
                    if (onTool != null) {
                        onTool.accept(tool);
                    }
                },
                activity -> {
                    runState.upsertActivity(activity);
                    if (onActivity != null) {
                        onActivity.accept(activity);
                    }
                });
        ChatDispatchResult result = dispatcher(scopeType).dispatch(context);
        List<ChatCitation> citations = wikiContext.citations().isEmpty()
                ? result.citations()
                : mergeCitations(result.citations(), wikiContext.citations());
        result = ChatDispatchResult.of(result.content(), runState.snapshot().reasoning(), result.usage(), citations);
        quotaService.recordUsage(userId, result.usage().totalTokens());
        return result;
    }

    private List<String> permissionSnapshot(ChatSendCmd cmd) {
        return cmd.getPermissionCodes() == null ? List.of() : List.copyOf(cmd.getPermissionCodes());
    }

    private ChatSession sessionFor(Long userId, ChatSendCmd cmd) {
        ChatSession session;
        if (cmd.getSessionId() != null) {
            session = sessionRepo.findById(cmd.getSessionId()).orElseThrow(() -> new BizException("会话不存在"));
            session.belongsTo(userId);
        } else {
            ChatScopeType scopeType = cmd.getScopeType() == null ? ChatScopeType.GENERAL : cmd.getScopeType();
            session = ChatSession.create(userId, truncate(cmd.getQuestion(), 40), scopeType);
            session.setAgentCode(cmd.getAgentCode());
            session.setSpaceSlug(cmd.getSpaceSlug());
            session.setProviderCode(cmd.getProviderCode());
            session.setModelCode(cmd.getModelCode());
            session = sessionRepo.save(session);
        }
        return session;
    }

    private ChatMessage persistUserMessage(ChatSession session, Long userId, ChatSendCmd cmd) {
        List<ChatAttachment> attachments = cmd.getAttachments() == null ? List.of() : cmd.getAttachments().stream()
                                                                                      .map(ChatAppService::toAttachment)
                                                                                      .toList();
        return messageRepo.save(ChatMessage.user(session.getId(), userId, cmd.getQuestion(), attachments));
    }

    private ChatMessage persistAssistantMessage(ChatSession session, Long userId) {
        return messageRepo.save(ChatMessage.assistant(session.getId(), userId));
    }

    private List<AiChatMessage> buildHistory(ChatSession session) {
        List<ChatMessage> messages = messageRepo.findBySessionId(session.getId()).stream()
                .filter(message -> message.getRole() == ChatMessageRole.USER || message.getRole() == ChatMessageRole.ASSISTANT)
                .filter(message -> StringUtils.hasText(message.getContent()))
                .toList();
        int from = Math.max(0, messages.size() - HISTORY_LIMIT * 2);
        return messages.subList(from, messages.size()).stream()
                .map(message -> new AiChatMessage(
                        message.getRole() == ChatMessageRole.USER ? "user" : "assistant",
                        message.getContent()))
                .toList();
    }

    private String effectiveQuestion(ChatSendCmd cmd) {
        List<String> documentTexts = cmd.getAttachments() == null
                ? List.of()
                : cmd.getAttachments().stream()
                  .filter(attachment -> StringUtils.hasText(attachment.extractedText()))
                  .map(attachment -> "【" + attachment.fileName() + "】\n" + attachment.extractedText())
                  .toList();
        if (documentTexts.isEmpty()) {
            return cmd.getQuestion();
        }
        return cmd.getQuestion() + "\n\n以下是附件抽取出的文本内容，请基于这些内容回答问题：\n\n" + String.join("\n\n", documentTexts);
    }

    private List<ChatCitation> mergeCitations(List<ChatCitation> first, List<ChatCitation> second) {
        Map<String, ChatCitation> citations = new java.util.LinkedHashMap<>();
        for (ChatCitation citation : first == null ? List.<ChatCitation>of() : first) {
            citations.put(citation.spaceSlug() + "|" + citation.nodeId() + "|" + citation.title(), citation);
        }
        for (ChatCitation citation : second == null ? List.<ChatCitation>of() : second) {
            citations.putIfAbsent(citation.spaceSlug() + "|" + citation.nodeId() + "|" + citation.title(), citation);
        }
        return List.copyOf(citations.values());
    }

    private ChatDispatcher dispatcher(ChatScopeType scopeType) {
        Map<ChatScopeType, ChatDispatcher> byScope = dispatchers.stream()
                .collect(Collectors.toMap(ChatDispatcher::scopeType, Function.identity(), (a, b) -> a));
        ChatDispatcher dispatcher = byScope.get(scopeType);
        if (dispatcher == null) {
            throw new BizException("当前会话类型不支持");
        }
        return dispatcher;
    }

    private ChatSendResultDTO resultOf(ChatSession session, ChatMessage userMessage, ChatMessage assistantMessage,
                                       UserChatQuota quota) {
        return new ChatSendResultDTO(
                String.valueOf(session.getId()),
                String.valueOf(userMessage.getId()),
                String.valueOf(assistantMessage.getId()),
                assistantMessage.getContent(),
                assistantMessage.getReasoning(),
                assistantMessage.getCitations(),
                assistantMessage.getTools(),
                assistantMessage.getActivities(),
                assistantMessage.getUsage(),
                quota.getUsedTokens(),
                quota.getLimitTokens(),
                quota.remaining());
    }

    private static final class ChatRunState {
        private final List<ChatActivity> activities = new ArrayList<>();
        private final List<ChatToolCall> tools = new ArrayList<>();
        private final StringBuilder reasoning = new StringBuilder();

        private synchronized void appendReasoning(String delta) {
            if (delta != null && !delta.isEmpty()) {
                reasoning.append(delta);
            }
        }

        private synchronized void addTool(AiAgentToolResult tool) {
            if (tool != null) {
                tools.add(toToolCall(tool));
            }
        }

        private synchronized void upsertActivity(ChatActivity activity) {
            if (activity == null) {
                return;
            }
            String key = activity.activityType() + "|" + activity.phase();
            for (int index = 0; index < activities.size(); index++) {
                ChatActivity current = activities.get(index);
                if ((current.activityType() + "|" + current.phase()).equals(key)) {
                    activities.set(index, activity);
                    return;
                }
            }
            activities.add(activity);
        }

        private synchronized ChatRunSnapshot snapshot() {
            return new ChatRunSnapshot(reasoning.toString(), List.copyOf(tools), List.copyOf(activities));
        }
    }

    private record ChatRunSnapshot(
            String reasoning,
            List<ChatToolCall> tools,
            List<ChatActivity> activities
    ) {
    }

    private static List<ChatActivity> settleActivities(List<ChatActivity> activities, String terminalStatus) {
        return activities.stream().map(activity -> isActiveStatus(activity.status())
                ? new ChatActivity(activity.activityType(), activity.phase(), terminalStatus, activity.title(),
                activity.content(), activity.query(), activity.hits(), activity.graph())
                : activity).toList();
    }

    private static List<ChatToolCall> settleTools(List<ChatToolCall> tools, String terminalStatus) {
        return tools.stream().map(tool -> isActiveStatus(tool.status())
                ? new ChatToolCall(tool.toolCallId(), tool.toolName(), terminalStatus, tool.message(), tool.payload())
                : tool).toList();
    }

    private static boolean isActiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return switch (status.trim().toLowerCase().replace('_', '-')) {
            case "running", "pending", "started", "executing", "processing", "in-progress" -> true;
            default -> false;
        };
    }

    private static ChatAttachment toAttachment(ChatAttachmentDTO dto) {
        return new ChatAttachment(
                dto.fileId() == null || dto.fileId().isBlank() ? null : Long.valueOf(dto.fileId()),
                dto.fileName(),
                dto.contentType(),
                dto.size(),
                dto.kind(),
                dto.url(),
                dto.extractedText(),
                dto.dataUrl());
    }

    private static ChatToolCall toToolCall(AiAgentToolResult tool) {
        return new ChatToolCall(
                tool.toolName() + "-" + UUID.randomUUID(),
                tool.toolName(),
                "complete",
                tool.message(),
                tool.payload());
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new BizException("当前用户未登录");
        }
    }
}
