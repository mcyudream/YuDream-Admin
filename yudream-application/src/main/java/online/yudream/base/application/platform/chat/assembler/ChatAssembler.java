package online.yudream.base.application.platform.chat.assembler;

import online.yudream.base.application.platform.chat.dto.ChatMessageDTO;
import online.yudream.base.application.platform.chat.dto.ChatQuotaDTO;
import online.yudream.base.application.platform.chat.dto.ChatSessionDTO;
import online.yudream.base.domain.platform.chat.aggregate.ChatMessage;
import online.yudream.base.domain.platform.chat.aggregate.ChatSession;
import online.yudream.base.domain.platform.chat.aggregate.UserChatQuota;

public final class ChatAssembler {

    private ChatAssembler() {
    }

    public static ChatSessionDTO session(ChatSession session) {
        if (session == null) {
            return null;
        }
        return new ChatSessionDTO(
                id(session.getId()),
                id(session.getUserId()),
                session.getTitle(),
                session.getScopeType(),
                session.getAgentCode(),
                session.getSpaceSlug(),
                session.getProviderCode(),
                session.getModelCode(),
                session.getMessageCount(),
                session.isPinned(),
                session.getLastMessageAt(),
                session.getCreateTime());
    }

    public static ChatMessageDTO message(ChatMessage message) {
        if (message == null) {
            return null;
        }
        return new ChatMessageDTO(
                id(message.getId()),
                id(message.getSessionId()),
                id(message.getUserId()),
                message.getRole(),
                message.getContent(),
                message.getReasoning(),
                message.getCitations(),
                message.getTools(),
                message.getActivities(),
                message.getAttachments(),
                message.getUsage(),
                message.getStatus(),
                message.getErrorMessage(),
                message.getCreateTime());
    }

    public static ChatQuotaDTO quota(UserChatQuota quota) {
        if (quota == null) {
            return null;
        }
        return new ChatQuotaDTO(
                id(quota.getUserId()),
                quota.getUsageDate(),
                quota.getUsedTokens(),
                quota.getLimitTokens(),
                quota.remaining(), quota.getReservedTokens(), quota.getUncertainTokens(),
                quota.getLimitTokens() >= online.yudream.base.application.platform.chat.service.ChatQuotaAppService.UNLIMITED_TOKENS);
    }

    public static java.util.List<online.yudream.base.domain.platform.chat.valobj.ChatToolCall> projectionTools(
            Object value, com.fasterxml.jackson.databind.ObjectMapper json, boolean waiting) {
        if (!(value instanceof java.util.List<?> tools)) return java.util.List.of();
        return tools.stream().map(tool -> json.convertValue(tool, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() { }))
                .map(tool -> new online.yudream.base.domain.platform.chat.valobj.ChatToolCall(text(tool.get("toolCallId")), text(tool.get("toolName")),
                        settledStatus(text(tool.get("status")), waiting, "complete"), text(tool.get("message")), objectMap(tool.get("payload"), json))).toList();
    }

    public static java.util.List<online.yudream.base.domain.platform.chat.valobj.ChatActivity> projectionActivities(
            Object value, com.fasterxml.jackson.databind.ObjectMapper json, boolean waiting) {
        if (!(value instanceof java.util.List<?> activities)) return java.util.List.of();
        return activities.stream().map(activity -> json.convertValue(activity, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() { }))
                .map(activity -> new online.yudream.base.domain.platform.chat.valobj.ChatActivity(text(activity.getOrDefault("activityType", "ai.progress")),
                        text(activity.getOrDefault("phase", activity.get("action"))),
                        settledStatus(text(activity.get("status")), waiting, "completed"), text(activity.getOrDefault("title", activity.get("content"))), text(activity.get("content")),
                        text(activity.get("query")), activity.get("hits") == null ? java.util.List.of() : json.convertValue(activity.get("hits"),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() { }), objectMap(activity.get("graph"), json))).toList();
    }

    private static String settledStatus(String status, boolean waiting, String completed) {
        if (status.isBlank() || java.util.Set.of("running", "executing", "pending", "waiting").contains(status)) return waiting ? "waiting" : completed;
        return status;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static java.util.Map<String, Object> objectMap(Object value, com.fasterxml.jackson.databind.ObjectMapper json) {
        return value == null ? java.util.Map.of() : json.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() { });
    }

    private static String id(Long value) {
        return value == null ? null : value.toString();
    }
}
