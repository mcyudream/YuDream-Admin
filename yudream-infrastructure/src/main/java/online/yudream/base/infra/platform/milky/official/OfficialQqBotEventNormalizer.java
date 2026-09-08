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
        String envelopeEventId = text(payload, "id", "event_id");
        return switch (type) {
            case "GROUP_AT_MESSAGE_CREATE", "GROUP_MESSAGE_CREATE", "C2C_MESSAGE_CREATE", "DIRECT_MESSAGE_CREATE",
                    "AT_MESSAGE_CREATE", "MESSAGE_CREATE" -> message(type, data, selfId, sessions, connectionId, envelopeEventId);
            case "INTERACTION_CREATE" -> interaction(data, selfId, sessions, connectionId, envelopeEventId);
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
                                             OfficialQqBotSessionStore sessions, Long connectionId,
                                             String envelopeEventId) {
        String scene = messageScene(type, data);
        String peerId = peerId(type, data, scene);
        String senderId = firstNonBlank(
                nested(data, "author", "member_openid"),
                nested(data, "author", "user_openid"),
                nested(data, "author", "id"),
                text(data, "author_id", "user_openid", "openid"));
        String msgId = firstNonBlank(text(data, "id", "msg_id"), nested(data, "message", "id"));
        String eventId = firstNonBlank(text(data, "event_id"), envelopeEventId);
        if (blank(selfId)) {
            selfId = botIdFromMentions(data);
            if (sessions != null && !blank(selfId)) {
                sessions.rememberSelf(connectionId, selfId);
            }
        }
        boolean mentionSelf = directedAtBot(type)
                || looksLikeBotMention(textContent(data), sessions == null ? null : sessions.selfName(connectionId));
        String selfName = sessions == null ? null : sessions.selfName(connectionId);
        List<Map<String, Object>> segments = segments(data, selfId, selfName);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message_scene", scene);
        payload.put("peer_id", peerId);
        if ("group".equals(scene)) {
            payload.put("group_id", peerId);
        }
        String channelId = firstNonBlank(text(data, "channel_id"), nested(data, "channel", "id"));
        String guildId = firstNonBlank(text(data, "guild_id"), nested(data, "guild", "id"));
        if (!blank(channelId)) {
            payload.put("channel_id", channelId);
        }
        if (!blank(guildId)) {
            payload.put("guild_id", guildId);
        }
        payload.put("user_id", senderId);
        payload.put("sender_id", senderId);
        payload.put("sender_nickname", firstNonBlank(nested(data, "author", "username"), nested(data, "author", "nickname")));
        payload.put("message_seq", msgId);
        payload.put("message_id", msgId);
        payload.put("msg_id", msgId);
        payload.put("event_id", eventId);
        payload.put("time", now(data));
        payload.put("segments", List.copyOf(segments));
        payload.put("raw_message", textContent(data));
        payload.put("native_type", type);
        payload.put("mention_self", mentionSelf);
        if (sessions != null) {
            if ("group".equals(scene)) {
                sessions.rememberGroup(connectionId, peerId, groupName(data));
                sessions.rememberGroupMember(connectionId, peerId, senderId, nested(data, "author", "username"));
            } else if ("friend".equals(scene)) {
                sessions.rememberFriend(connectionId, peerId, nested(data, "author", "username"));
            } else if (!blank(guildId)) {
                sessions.rememberGuild(connectionId, guildId, firstNonBlank(nested(data, "guild", "name"), text(data, "guild_name")));
            }
            sessions.rememberInbound(connectionId, peerId, msgId, eventId);
            sessions.rememberMessage(connectionId, scene, peerId, payload);
            if (!blank(selfId)) {
                sessions.rememberSelf(connectionId, selfId);
            }
        }
        return new MilkyModels.Event(now(data), selfId, "message_receive", payload);
    }

    static String messageScene(String type, JsonNode data) {
        if ("DIRECT_MESSAGE_CREATE".equals(type)) {
            return "dm";
        }
        if ("AT_MESSAGE_CREATE".equals(type) || "MESSAGE_CREATE".equals(type)) {
            return "channel";
        }
        if (type != null && type.startsWith("GROUP") || !blank(text(data, "group_openid", "group_id"))) {
            return "group";
        }
        if ("C2C_MESSAGE_CREATE".equals(type) || blank(text(data, "channel_id")) && blank(nested(data, "channel", "id"))) {
            return "friend";
        }
        return "channel";
    }

    static String peerId(String type, JsonNode data, String scene) {
        return switch (scene) {
            case "group" -> firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
            case "channel" -> firstNonBlank(text(data, "channel_id"), nested(data, "channel", "id"));
            case "dm" -> firstNonBlank(text(data, "channel_id"), nested(data, "channel", "id"), text(data, "guild_id"));
            default -> firstNonBlank(text(data, "user_openid", "openid"), nested(data, "author", "id"), nested(data, "author", "user_openid"));
        };
    }

    private static MilkyModels.Event interaction(JsonNode data, String selfId,
                                                 OfficialQqBotSessionStore sessions, Long connectionId,
                                                 String envelopeEventId) {
        String interactionId = firstNonBlank(text(data, "id"), envelopeEventId);
        String buttonId = interactionButtonId(data);
        String content = interactionText(data);
        String scene = interactionScene(data);
        String peerId = interactionPeerId(data, scene);
        String senderId = firstNonBlank(
                text(data, "group_member_openid", "user_openid", "member_openid", "openid"),
                nested(data, "user", "id"),
                nested(data, "author", "member_openid"),
                nested(data, "author", "user_openid"),
                nested(data, "author", "id"));
        String msgId = firstNonBlank(nested(data, "message", "id"), interactionId);
        String eventId = firstNonBlank(text(data, "event_id"), envelopeEventId, interactionId);
        if ((blank(buttonId) || keyboardOrCommandInteraction(data)) && !blank(content)) {
            return interactionMessage(data, selfId, sessions, connectionId, scene, peerId, senderId,
                    content, msgId, eventId, interactionId);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("button_id", buttonId);
        payload.put("message_scene", scene);
        payload.put("peer_id", peerId);
        if ("group".equals(scene)) {
            payload.put("group_id", peerId);
        } else if ("channel".equals(scene)) {
            payload.put("channel_id", peerId);
        }
        payload.put("user_id", senderId);
        payload.put("sender_id", senderId);
        payload.put("message_seq", msgId);
        payload.put("msg_id", msgId);
        payload.put("event_id", eventId);
        payload.put("interaction_id", interactionId);
        payload.put("native_type", "INTERACTION_CREATE");
        payload.put("native", map(data));
        rememberInteraction(sessions, connectionId, scene, peerId, senderId, data, msgId, eventId);
        return new MilkyModels.Event(now(data), selfId, "button_click", payload);
    }

    private static MilkyModels.Event interactionMessage(JsonNode data, String selfId,
                                                        OfficialQqBotSessionStore sessions, Long connectionId,
                                                        String scene, String peerId, String senderId,
                                                        String content, String msgId, String eventId,
                                                        String interactionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message_scene", scene);
        payload.put("peer_id", peerId);
        if ("group".equals(scene)) {
            payload.put("group_id", peerId);
        } else if ("channel".equals(scene) || "dm".equals(scene)) {
            payload.put("channel_id", peerId);
        }
        String guildId = firstNonBlank(text(data, "guild_id"), nested(data, "guild", "id"));
        if (!blank(guildId)) {
            payload.put("guild_id", guildId);
        }
        payload.put("user_id", senderId);
        payload.put("sender_id", senderId);
        payload.put("sender_nickname", firstNonBlank(
                nested(data, "user", "username"),
                nested(data, "author", "username"),
                nested(data, "author", "nickname")));
        payload.put("message_seq", msgId);
        payload.put("message_id", msgId);
        payload.put("msg_id", msgId);
        payload.put("event_id", eventId);
        payload.put("interaction_id", interactionId);
        payload.put("time", now(data));
        payload.put("segments", List.of(Map.of("type", "text", "data", Map.of("text", content))));
        payload.put("raw_message", content);
        payload.put("native_type", "INTERACTION_CREATE");
        payload.put("mention_self", true);
        payload.put("native", map(data));
        rememberInteraction(sessions, connectionId, scene, peerId, senderId, data, msgId, eventId);
        if (sessions != null) {
            sessions.rememberMessage(connectionId, scene, peerId, payload);
        }
        return new MilkyModels.Event(now(data), selfId, "message_receive", payload);
    }

    private static void rememberInteraction(OfficialQqBotSessionStore sessions, Long connectionId,
                                            String scene, String peerId, String senderId, JsonNode data,
                                            String msgId, String eventId) {
        if (sessions == null || blank(peerId)) {
            return;
        }
        if ("group".equals(scene)) {
            sessions.rememberGroup(connectionId, peerId, groupName(data));
            sessions.rememberGroupMember(connectionId, peerId, senderId,
                    firstNonBlank(nested(data, "user", "username"), nested(data, "author", "username")));
        } else if ("friend".equals(scene)) {
            sessions.rememberFriend(connectionId, peerId,
                    firstNonBlank(nested(data, "user", "username"), nested(data, "author", "username")));
        } else if (!blank(firstNonBlank(text(data, "guild_id"), nested(data, "guild", "id")))) {
            sessions.rememberGuild(connectionId, firstNonBlank(text(data, "guild_id"), nested(data, "guild", "id")),
                    firstNonBlank(nested(data, "guild", "name"), text(data, "guild_name")));
        }
        sessions.rememberInbound(connectionId, peerId, msgId, eventId);
    }

    static String interactionButtonId(JsonNode data) {
        return firstNonBlank(
                text(data, "data.resolved.button.id"),
                text(data, "data.resolved.button_id"),
                text(data, "data.button.id"),
                text(data, "data.button_id"),
                text(data, "resolved.button.id"),
                text(data, "resolved.button_id"),
                text(data, "button_id"));
    }

    static boolean keyboardOrCommandInteraction(JsonNode data) {
        String type = firstNonBlank(text(data, "data.type"), text(data, "type"));
        if (blank(type)) {
            return !blank(interactionText(data));
        }
        String normalized = type.trim().toLowerCase(java.util.Locale.ROOT);
        return "11".equals(normalized)
                || "keyboard".equals(normalized)
                || "command".equals(normalized)
                || "send_message".equals(normalized)
                || "2".equals(normalized);
    }

    static String interactionText(JsonNode data) {
        String explicit = firstNonBlank(
                text(data, "data.resolved.button_data"),
                text(data, "data.resolved.button.data"),
                text(data, "data.resolved.user_input"),
                text(data, "data.resolved.input"),
                text(data, "data.resolved.message_content"),
                text(data, "data.resolved.content"),
                text(data, "data.resolved.text"),
                text(data, "data.button_data"),
                text(data, "data.send_message"),
                text(data, "data.content"),
                text(data, "content"),
                text(data, "chat_content"),
                nested(data, "message", "content")
        );
        if (!blank(explicit)) {
            return stripBotMentions(explicit, null);
        }
        String name = firstNonBlank(text(data, "data.name"), text(data, "name"));
        if (commandLike(name)) {
            return stripBotMentions(name, null);
        }
        return null;
    }

    private static boolean commandLike(String value) {
        if (blank(value)) {
            return false;
        }
        String source = value.trim();
        return source.startsWith("/") || source.startsWith("!")
                || "菜单".equals(source) || "帮助".equals(source) || "菜单指令".equals(source);
    }

    static String interactionScene(JsonNode data) {
        String scene = firstNonBlank(text(data, "scene"), nested(data, "data", "scene"));
        if (!blank(scene)) {
            return switch (scene.toLowerCase(java.util.Locale.ROOT)) {
                case "group" -> "group";
                case "c2c", "friend", "private" -> "friend";
                case "dms", "dm" -> "dm";
                case "guild", "channel" -> "channel";
                default -> scene.toLowerCase(java.util.Locale.ROOT);
            };
        }
        String chatType = firstNonBlank(text(data, "chat_type"), nested(data, "data", "chat_type"));
        if ("1".equals(chatType) || "group".equalsIgnoreCase(chatType)) {
            return "group";
        }
        if ("2".equals(chatType) || "c2c".equalsIgnoreCase(chatType)) {
            return "friend";
        }
        if ("0".equals(chatType) || "guild".equalsIgnoreCase(chatType)) {
            return "channel";
        }
        if (!blank(text(data, "group_openid", "group_id"))) {
            return "group";
        }
        if (!blank(text(data, "channel_id")) || !blank(text(data, "guild_id"))) {
            return "channel";
        }
        return "friend";
    }

    static String interactionPeerId(JsonNode data, String scene) {
        return switch (scene) {
            case "group" -> firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
            case "channel" -> firstNonBlank(text(data, "channel_id"), nested(data, "channel", "id"));
            case "dm" -> firstNonBlank(text(data, "channel_id"), nested(data, "channel", "id"), text(data, "guild_id"));
            default -> firstNonBlank(text(data, "user_openid", "openid"), nested(data, "user", "id"));
        };
    }

    private static MilkyModels.Event groupJoin(JsonNode data, String selfId,
                                               OfficialQqBotSessionStore sessions, Long connectionId) {
        String groupId = firstNonBlank(text(data, "group_openid", "group_id"), nested(data, "group", "id"));
        String userId = firstNonBlank(text(data, "op_member_openid", "op_userid", "user_openid"), nested(data, "op_user", "id"));
        if (sessions != null) {
            sessions.rememberGroup(connectionId, groupId, groupName(data));
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
        String userId = firstNonBlank(text(data, "member_openid", "user_openid", "op_member_openid"),
                nested(data, "member", "id"), nested(data, "op_user", "id"));
        String requestId = firstNonBlank(text(data, "join_request_id", "id"), userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", groupId);
        payload.put("user_id", userId);
        payload.put("request_id", requestId);
        payload.put("join_request_id", firstNonBlank(text(data, "join_request_id"), requestId));
        payload.put("comment", joinComment(data));
        payload.put("username", text(data, "username"));
        payload.put("native_type", "GROUP_JOIN_REQUEST");
        payload.put("connection_id", connectionId);
        payload.put("native", map(data));
        return new MilkyModels.Event(now(data), selfId, "group_request", payload);
    }

    /** 官方入群申请没有顶层 comment，验证文本在 verify_info.verify_message / review_qa_list。 */
    private static String joinComment(JsonNode data) {
        String comment = firstNonBlank(
                text(data, "comment", "verify_message"),
                text(data, "verify_info.verify_message"));
        if (!blank(comment)) {
            return comment;
        }
        JsonNode qaList = data.path("verify_info").path("review_qa_list");
        if (!qaList.isArray() || qaList.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode qa : qaList) {
            String question = text(qa, "question");
            String answer = text(qa, "answer");
            if (blank(question) && blank(answer)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            if (!blank(question)) {
                builder.append(question).append('：');
            }
            if (!blank(answer)) {
                builder.append(answer);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
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
        String username = firstNonBlank(nested(data, "user", "username"), nested(data, "user", "nickname"));
        if (sessions != null && !blank(userId)) {
            sessions.rememberSelf(connectionId, userId, username);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", userId);
        payload.put("nickname", username);
        payload.put("native_type", "READY");
        return new MilkyModels.Event(now(data), userId, "ready", payload);
    }

    static List<Map<String, Object>> segments(JsonNode data) {
        return segments(data, null, null);
    }

    static List<Map<String, Object>> segments(JsonNode data, String selfId) {
        return segments(data, selfId, null);
    }

    static List<Map<String, Object>> segments(JsonNode data, String selfId, String selfName) {
        List<Map<String, Object>> segments = new ArrayList<>();
        String content = stripBotMentions(textContent(data), selfId, selfName);
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
                if (blank(userId) || botMention(userId, selfId, mention)) {
                    continue;
                }
                segments.add(Map.of("type", "mention", "data", Map.of("user_id", userId)));
            }
        }
        return List.copyOf(segments);
    }

    static boolean directedAtBot(String type) {
        return "GROUP_AT_MESSAGE_CREATE".equals(type)
                || "AT_MESSAGE_CREATE".equals(type)
                || "C2C_MESSAGE_CREATE".equals(type)
                || "DIRECT_MESSAGE_CREATE".equals(type);
    }

    static boolean looksLikeBotMention(String content, String selfName) {
        if (blank(content)) {
            return false;
        }
        String source = content.trim();
        if (source.matches("(?is)^<@!?[^>]+>(?:\\s+.*)?$")) {
            return true;
        }
        if (!blank(selfName) && source.matches("(?is)^@" + java.util.regex.Pattern.quote(selfName.trim()) + "(?:\\s+.*)?$")) {
            return true;
        }
        return source.matches("(?is)^@\\S+(?:\\s+.*)?$");
    }

    static String botIdFromMentions(JsonNode data) {
        JsonNode mentions = data.path("mentions");
        if (!mentions.isArray()) {
            return null;
        }
        for (JsonNode mention : mentions) {
            boolean bot = mention.path("bot").asBoolean(false) || "true".equalsIgnoreCase(text(mention, "bot"));
            if (!bot) {
                continue;
            }
            return firstNonBlank(text(mention, "id"), text(mention, "user_openid", "member_openid"));
        }
        return null;
    }

    static String stripBotMentions(String content, String selfId) {
        return stripBotMentions(content, selfId, null);
    }

    static String stripBotMentions(String content, String selfId, String selfName) {
        if (blank(content)) {
            return content;
        }
        String stripped = content.replaceAll("(?i)<@!?[^>]+>", " ");
        stripped = stripped.replaceAll("(?i)\\[@?\\d+\\]", " ");
        if (!blank(selfId)) {
            String quoted = java.util.regex.Pattern.quote(selfId);
            stripped = stripped.replaceAll("(?i)<@!?" + quoted + ">", " ");
            stripped = stripped.replaceAll("(?i)\\[@?" + quoted + "\\]", " ");
            stripped = stripped.replaceAll("(?i)@" + quoted + "\\b", " ");
        }
        if (!blank(selfName)) {
            String quotedName = java.util.regex.Pattern.quote(selfName.trim());
            stripped = stripped.replaceAll("(?i)^\\s*@" + quotedName + "(?:\\s+|$)", " ");
            stripped = stripped.replaceAll("(?i)\\s+@" + quotedName + "(?=\\s|$)", " ");
        }
        stripped = stripped.replaceAll("(?i)^\\s*@\\S+\\s+", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private static boolean botMention(String userId, String selfId, JsonNode mention) {
        if (!blank(selfId) && selfId.equals(userId)) {
            return true;
        }
        return mention.path("bot").asBoolean(false) || "true".equalsIgnoreCase(text(mention, "bot"));
    }

    static String groupName(JsonNode data) {
        return firstNonBlank(
                text(data, "group_name", "name"),
                nested(data, "group", "name"),
                nested(data, "group", "group_name"));
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
