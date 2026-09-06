package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.databind.JsonNode;
import online.yudream.base.domain.platform.milky.model.MilkyModels;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把官方 Gateway/Webhook 事件归一成宿主内部 {@link MilkyModels.Event}。
 */
public final class OfficialQqBotEventNormalizer {
    private OfficialQqBotEventNormalizer() { }

    public static MilkyModels.Event normalize(JsonNode payload, OfficialQqBotSessionStore sessions, Long connectionId) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        String type = text(payload, "t", "event_type", "type");
        if (blank(type)) {
            return null;
        }
        JsonNode data = payload.has("d") ? payload.get("d") : payload.path("data");
        if (data == null || data.isMissingNode() || data.isNull()) {
            data = payload;
        }
        String selfId = sessions == null ? null : sessions.selfId(connectionId);
        return switch (type) {
            case "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE", "C2C_MESSAGE_CREATE", "DIRECT_MESSAGE_CREATE",
                    "AT_MESSAGE_CREATE", "MESSAGE_CREATE" -> message(type, data, selfId, sessions, connectionId);
            case "INTERACTION_CREATE" -> interaction(data, selfId, sessions, connectionId);
            case "GROUP_ADD_ROBOT" -> groupJoin(data, selfId, sessions, connectionId);
            case "GROUP_DEL_ROBOT" -> groupLeave(data, selfId, sessions, connectionId);
            case "GROUP_JOIN_REQUEST" -> groupJoinRequest(data, selfId, connectionId);
            case "GROUP_MEMBER_ADD" -> groupMemberChange(data, selfId, "group_member_increase");
            case "GROUP_MEMBER_REMOVE" -> groupMemberChange(data, selfId, "group_member_decrease");
            case "FRIEND_ADD" -> friendAdd(data, selfId, sessions, connectionId);
            case "FRIEND_DEL" -> friendDel(data, selfId, sessions, connectionId);
            case "C2C_MSG_REJECT", "GROUP_MSG_REJECT" -> notice(type, data, selfId, "message_reject");
            case "C2C_MSG_RECEIVE", "GROUP_MSG_RECEIVE" -> notice(type, data, selfId, "message_receive_allowed");
            case "SUBSCRIBE_MESSAGE_STATUS" -> notice(type, data, selfId, "subscribe_message_status");
            case "GUILD_CREATE", "GUILD_UPDATE", "GUILD_DELETE",
                    "CHANNEL_CREATE", "CHANNEL_UPDATE", "CHANNEL_DELETE" -> notice(type, data, selfId, type.toLowerCase());
            case "READY" -> ready(data, selfId, sessions, connectionId);
            default -> new MilkyModels.Event(now(data), selfId, type.toLowerCase(), map(data));
        };
    }

    private static MilkyModels.Event message(String type, JsonNode data, String selfId,
                                             OfficialQqBotSessionStore sessions, Long connectionId) {
        boolean group = type.startsWith("GROUP") || !blank(text(data, "group_openid", "group_id"));
        String peerId = group
                ? firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"))
                : firstNonBlank(text(data, "user_openid", "openid", "author.user_openid"), nested(data, "author", "id"));
        String senderId = firstNonBlank(
                nested(data, "author", "member_openid"),
                nested(data, "author", "user_openid"),
                nested(data, "author", "id"),
                text(data, "author_id", "user_openid", "openid"));
        String msgId = firstNonBlank(text(data, "id", "msg_id"), nested(data, "message", "id"));
        String eventId = text(data, "event_id");
        List<Map<String, Object>> segments = segments(data);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message_scene", group ? "group" : "friend");
        payload.put("peer_id", peerId);
        if (group) {
            payload.put("group_id", peerId);
        }
        payload.put("user_id", senderId);
        payload.put("sender_id", senderId);
        payload.put("sender_nickname", firstNonBlank(nested(data, "author", "username"), nested(data, "author", "nickname")));
        payload.put("message_seq", msgId);
        payload.put("message_id", msgId);
        payload.put("msg_id", msgId);
        payload.put("event_id", eventId);
        payload.put("time", now(data));
        payload.put("segments", segments);
        payload.put("raw_message", textContent(data));
        payload.put("native_type", type);
        if (sessions != null) {
            if (group) {
                sessions.rememberGroup(connectionId, peerId, null);
            } else {
                sessions.rememberFriend(connectionId, peerId, nested(data, "author", "username"));
            }
            sessions.rememberInbound(connectionId, peerId, msgId, eventId);
            if (!blank(selfId)) {
                sessions.rememberSelf(connectionId, selfId);
            }
        }
        return new MilkyModels.Event(now(data), selfId, "message_receive", payload);
    }

    private static MilkyModels.Event interaction(JsonNode data, String selfId,
                                                 OfficialQqBotSessionStore sessions, Long connectionId) {
        JsonNode button = data.path("data").path("resolved").path("button");
        if (button.isMissingNode()) {
            button = data.path("data").path("button");
        }
        String buttonId = firstNonBlank(text(button, "id", "button_id"), nested(data, "data", "id"));
        String peerId = firstNonBlank(text(data, "group_openid", "group_id"), text(data, "user_openid", "openid"));
        String senderId = firstNonBlank(text(data, "user_openid", "member_openid", "openid"), nested(data, "user", "id"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("button_id", buttonId);
        payload.put("peer_id", peerId);
        payload.put("group_id", text(data, "group_openid", "group_id"));
        payload.put("user_id", senderId);
        payload.put("sender_id", senderId);
        payload.put("message_seq", firstNonBlank(text(data, "id"), nested(data, "message", "id")));
        payload.put("native_type", "INTERACTION_CREATE");
        payload.put("native", map(data));
        if (sessions != null && !blank(peerId)) {
            sessions.rememberInbound(connectionId, peerId, text(data, "id"), text(data, "event_id"));
        }
        return new MilkyModels.Event(now(data), selfId, "button_click", payload);
    }

    private static MilkyModels.Event groupJoin(JsonNode data, String selfId,
                                               OfficialQqBotSessionStore sessions, Long connectionId) {
        String groupId = firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
        String userId = firstNonBlank(text(data, "op_member_openid", "op_userid", "user_openid"), nested(data, "op_user", "id"));
        if (sessions != null) {
            sessions.rememberGroup(connectionId, groupId, null);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        payload.put("request_id", firstNonBlank(text(data, "id"), groupId));
        payload.put("comment", text(data, "comment"));
        payload.put("native_type", "GROUP_ADD_ROBOT");
        return new MilkyModels.Event(now(data), selfId, "group_request", payload);
    }

    private static MilkyModels.Event groupLeave(JsonNode data, String selfId,
                                                OfficialQqBotSessionStore sessions, Long connectionId) {
        String groupId = firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
        if (sessions != null) {
            sessions.forgetGroup(connectionId, groupId);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", groupId);
        payload.put("native_type", "GROUP_DEL_ROBOT");
        return new MilkyModels.Event(now(data), selfId, "group_leave", payload);
    }

    private static MilkyModels.Event groupJoinRequest(JsonNode data, String selfId, Long connectionId) {
        String groupId = firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
        String userId = firstNonBlank(text(data, "op_member_openid", "member_openid", "user_openid"), nested(data, "op_user", "id"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        payload.put("request_id", firstNonBlank(text(data, "id"), userId));
        payload.put("comment", text(data, "comment"));
        payload.put("native_type", "GROUP_JOIN_REQUEST");
        payload.put("connection_id", connectionId);
        return new MilkyModels.Event(now(data), selfId, "group_request", payload);
    }

    private static MilkyModels.Event groupMemberChange(JsonNode data, String selfId, String eventType) {
        String groupId = firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
        String userId = firstNonBlank(text(data, "member_openid", "op_member_openid", "user_openid"), nested(data, "member", "id"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        payload.put("native_type", eventType.equals("group_member_increase") ? "GROUP_MEMBER_ADD" : "GROUP_MEMBER_REMOVE");
        payload.put("native", map(data));
        return new MilkyModels.Event(now(data), selfId, eventType, payload);
    }

    private static MilkyModels.Event notice(String type, JsonNode data, String selfId, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>(map(data));
        payload.put("native_type", type);
        String groupId = firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
        String userId = firstNonBlank(text(data, "user_openid", "openid", "member_openid"), nested(data, "user", "id"));
        if (!blank(groupId)) {
            payload.put("group_id", groupId);
        }
        if (!blank(userId)) {
            payload.put("user_id", userId);
        }
        return new MilkyModels.Event(now(data), selfId, eventType, payload);
    }

    private static MilkyModels.Event friendAdd(JsonNode data, String selfId,
                                               OfficialQqBotSessionStore sessions, Long connectionId) {
        String userId = firstNonBlank(text(data, "user_openid", "openid"), nested(data, "user", "id"));
        if (sessions != null) {
            sessions.rememberFriend(connectionId, userId, null);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("native_type", "FRIEND_ADD");
        return new MilkyModels.Event(now(data), selfId, "friend_add", payload);
    }

    private static MilkyModels.Event friendDel(JsonNode data, String selfId,
                                               OfficialQqBotSessionStore sessions, Long connectionId) {
        String userId = firstNonBlank(text(data, "user_openid", "openid"), nested(data, "user", "id"));
        if (sessions != null) {
            sessions.forgetFriend(connectionId, userId);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("native_type", "FRIEND_DEL");
        return new MilkyModels.Event(now(data), selfId, "friend_del", payload);
    }

    private static MilkyModels.Event ready(JsonNode data, String selfId,
                                           OfficialQqBotSessionStore sessions, Long connectionId) {
        String userId = firstNonBlank(nested(data, "user", "id"), nested(data, "user", "openid"), selfId);
        if (sessions != null && !blank(userId)) {
            sessions.rememberSelf(connectionId, userId);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("nickname", nested(data, "user", "username"));
        payload.put("native_type", "READY");
        return new MilkyModels.Event(now(data), userId, "ready", payload);
    }

    static List<Map<String, Object>> segments(JsonNode data) {
        List<Map<String, Object>> segments = new ArrayList<>();
        String content = textContent(data);
        if (!blank(content)) {
            segments.add(Map.of("type", "text", "data", Map.of("text", content)));
        }
        JsonNode attachments = data.path("attachments");
        if (attachments.isArray()) {
            for (JsonNode attachment : attachments) {
                String url = firstNonBlank(text(attachment, "url"), text(attachment, "resource_url"));
                String contentType = text(attachment, "content_type");
                String type = contentType != null && contentType.startsWith("image/") ? "image"
                        : contentType != null && contentType.startsWith("audio/") ? "record"
                        : contentType != null && contentType.startsWith("video/") ? "video"
                        : "file";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("uri", url);
                payload.put("url", url);
                payload.put("file", url);
                segments.add(Map.of("type", type, "data", payload));
            }
        }
        JsonNode messageReference = data.path("message_reference");
        if (!messageReference.isMissingNode() && !messageReference.isNull()) {
            String replyId = firstNonBlank(text(messageReference, "message_id"), text(messageReference, "id"));
            if (!blank(replyId)) {
                segments.add(0, Map.of("type", "reply", "data", Map.of("message_seq", replyId)));
            }
        }
        JsonNode mentions = data.path("mentions");
        if (mentions.isArray()) {
            for (JsonNode mention : mentions) {
                String userId = firstNonBlank(text(mention, "id"), text(mention, "user_openid", "member_openid"));
                if (!blank(userId)) {
                    segments.add(Map.of("type", "mention", "data", Map.of("user_id", userId)));
                }
            }
        }
        return List.copyOf(segments);
    }

    private static String textContent(JsonNode data) {
        return firstNonBlank(text(data, "content"), nested(data, "message", "content"));
    }

    private static long now(JsonNode data) {
        String timestamp = firstNonBlank(text(data, "timestamp"), nested(data, "message", "timestamp"));
        if (!blank(timestamp)) {
            try {
                return OffsetDateTime.parse(timestamp).toEpochSecond();
            } catch (RuntimeException ignored) {
                try {
                    return Instant.parse(timestamp).getEpochSecond();
                } catch (RuntimeException ignoredAgain) {
                    // fall through
                }
            }
        }
        return Instant.now().getEpochSecond();
    }

    private static Map<String, Object> map(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Map.of();
        }
        if (node.isObject()) {
            Map<String, Object> copied = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> copied.put(entry.getKey(), jsonValue(entry.getValue())));
            return copied;
        }
        return Map.of("value", jsonValue(node));
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            return map(node);
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asText();
    }

    private static String nested(JsonNode node, String parent, String child) {
        return text(node.path(parent), child);
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String key : keys) {
            if (key.contains(".")) {
                String[] parts = key.split("\\.", 2);
                String nested = text(node.path(parts[0]), parts[1]);
                if (!blank(nested)) {
                    return nested;
                }
                continue;
            }
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && !value.isMissingNode() && !value.asText("").isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!blank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
