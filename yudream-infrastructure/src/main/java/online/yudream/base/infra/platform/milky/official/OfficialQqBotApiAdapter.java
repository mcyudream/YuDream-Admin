package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;
import online.yudream.base.domain.platform.milky.model.MilkyModels.Event;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 官方 OpenAPI 适配器：共享 Milky 方法名映射到 REST；未映射的路径/方法作为特异化入口透传。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OfficialQqBotApiAdapter {
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    /** 官方富媒体必填但不可见的 caption，避免普通空格把图片压成缩略图。 */
    static final String RICH_MEDIA_PLACEHOLDER = "\u200B";
    private static final Pattern HTTP_PREFIX = Pattern.compile("^(GET|POST|PUT|PATCH|DELETE)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private final OfficialQqBotAccessTokenClient tokens;
    private final OfficialQqBotSessionStore sessions;
    private final ObjectMapper mapper = new ObjectMapper();

    public Object invoke(Context context, String api, Object body) {
        Map<String, Object> payload = map(body);
        String method = api == null ? "" : api.trim();
        if (method.isBlank()) {
            throw new BizException("官方机器人 API 名称不能为空");
        }
        return switch (method) {
            case "get_login_info" -> loginInfo(context);
            case "get_group_list" -> resolveGroupList(context);
            case "get_friend_list" -> Map.of("friends", sessions.friendList(context.connectionId()));
            case "get_group_info" -> groupInfo(context, payload);
            case "get_group_member_info" -> groupMember(context, payload);
            case "get_group_member_list" -> groupMembers(context, payload);
            case "get_resource_temp_url" -> resourceTempUrl(payload);
            case "get_friend_info" -> friendInfo(text(payload, "user_id", "user_openid", "openid"));
            case "get_history_messages" -> history(context, payload);
            case "get_message" -> message(context, payload);
            case "get_forwarded_messages" -> Map.of("messages", List.of());
            case "send_group_message" -> sendMessage(context, true, payload);
            case "send_private_message" -> sendMessage(context, false, payload);
            case "send_private_stream_message" -> streamPrivateMessage(context, payload);
            case "recall_group_message", "recall_private_message" -> recall(context, payload);
            case "upload_group_file" -> uploadFile(context, true, payload);
            case "upload_private_file" -> uploadFile(context, false, payload);
            case "set_group_kick" -> kickMembers(context, payload);
            case "set_group_ban" -> restrictChat(context, payload, true);
            case "get_group_ban" -> restrictChat(context, payload, false);
            case "get_group_join_requests" -> joinRequests(context, payload);
            case "set_group_add_request" -> approveJoin(context, payload);
            case "get_official_menu" -> request(context, HttpMethod.GET, "/v2/menu", null);
            case "set_official_menu" -> request(context, HttpMethod.PUT, "/v2/menu", payload);
            case "get_official_panels" -> request(context, HttpMethod.GET, panelsQueryPath(payload), null);
            case "create_official_panel" -> request(context, HttpMethod.POST, "/v2/panels", payload);
            case "set_official_panel" -> updatePanel(context, payload);
            case "ack_official_interaction" -> ackInteraction(context, payload);
            case "send_channel_message" -> sendChannelMessage(context, payload);
            case "send_guild_dm" -> sendGuildDm(context, payload);
            case "create_guild_dm" -> createGuildDm(context, payload);
            case "recall_channel_message" -> recallChannelMessage(context, payload);
            case "get_guild_list" -> guildList(context, payload);
            case "set_guild_mute" -> setGuildMute(context, payload);
            case "set_guild_member_mute" -> setGuildMemberMute(context, payload);
            case "set_guild_members_mute" -> setGuildMembersMute(context, payload);
            case "get_group_bot_state" -> groupBotState(context, payload);
            case "get_join_approval_strategy" -> joinApprovalStrategy(context, payload);
            default -> raw(context, method, payload);
        };
    }

    private Object updatePanel(Context context, Map<String, Object> payload) {
        String panelId = required(text(payload, "panel_id"), "面板 ID 不能为空");
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.remove("panel_id");
        if (!body.containsKey("panel")) {
            body = Map.of("panel", new LinkedHashMap<>(body));
        }
        return request(context, HttpMethod.PUT, "/v2/panels/" + panelId, body);
    }

    public void ackInteractionIfNeeded(Context context, Event event) {
        if (event == null || event.data() == null) {
            return;
        }
        if (!"INTERACTION_CREATE".equals(String.valueOf(event.data().get("native_type")))) {
            return;
        }
        String interactionId = firstNonBlank(
                text(event.data(), "interaction_id"),
                text(event.data(), "id"));
        if (blank(interactionId)) {
            return;
        }
        try {
            ackInteraction(context, Map.of("interaction_id", interactionId));
        } catch (RuntimeException exception) {
            log.warn("Official interaction ack failed: connectionId={}, interactionId={}",
                    context == null ? null : context.connectionId(), interactionId);
        }
    }

    private Object ackInteraction(Context context, Map<String, Object> payload) {
        String interactionId = required(text(payload, "interaction_id", "id"), "交互 ID 不能为空");
        Map<String, Object> body = new LinkedHashMap<>();
        int code = payload.get("code") instanceof Number number ? number.intValue() : 0;
        body.put("code", code);
        Object ackPayload = payload.get("payload");
        if (ackPayload instanceof Map<?, ?> map) {
            body.put("data", map);
        }
        return request(context, HttpMethod.PUT, "/interactions/" + interactionId, body);
    }

    private static String panelsQueryPath(Map<String, Object> payload) {
        String scope = required(text(payload, "scope"), "指令面板场景不能为空");
        StringBuilder path = new StringBuilder("/v2/panels?scope=").append(encodeQuery(scope));
        String cursor = text(payload, "cursor");
        String limit = text(payload, "limit");
        if (!blank(cursor)) {
            path.append("&cursor=").append(encodeQuery(cursor));
        }
        if (!blank(limit)) {
            path.append("&limit=").append(encodeQuery(limit));
        }
        return path.toString();
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private Object loginInfo(Context context) {
        Object users = request(context, HttpMethod.GET, "/users/@me", null);
        Map<String, Object> user = map(users);
        String id = firstNonBlank(text(user, "id", "openid", "user_openid", "bot_id"), context.appId());
        if (id != null) {
            sessions.rememberSelf(context.connectionId(), id);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", id);
        result.put("nickname", firstNonBlank(text(user, "username", "nickname"), context.appId()));
        result.put("app_id", context.appId());
        result.put("protocol", "official");
        return result;
    }

    private Object resolveGroupList(Context context) {
        List<Map<String, Object>> groups = sessions.groupList(context.connectionId());
        for (Map<String, Object> group : groups) {
            String groupId = text(group, "group_id", "group_openid");
            if (!sessions.needsGroupName(context.connectionId(), groupId)) {
                continue;
            }
            try {
                groupInfo(context, Map.of("group_id", groupId));
            } catch (RuntimeException exception) {
                log.debug("官方群资料拉取失败，保留 openid 作为展示名: groupId={}, errorType={}",
                        groupId, exception.getClass().getSimpleName());
            }
        }
        return Map.of("groups", sessions.groupList(context.connectionId()));
    }

    private Object groupInfo(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        Object result = request(context, HttpMethod.GET, "/v2/groups/" + groupId + "/info", null);
        Map<String, Object> mapped = new LinkedHashMap<>(map(result));
        mapped.putIfAbsent("group_id", firstNonBlank(text(mapped, "group_openid", "group_id"), groupId));
        String groupName = firstNonBlank(
                text(mapped, "group_name", "name", "group_remark"),
                nestedName(mapped.get("group")));
        mapped.put("group_name", firstNonBlank(groupName, groupId));
        sessions.rememberGroup(context.connectionId(), groupId, groupName);
        return mapped;
    }

    private static String nestedName(Object value) {
        if (!(value instanceof Map<?, ?> nested)) {
            return null;
        }
        Object name = nested.containsKey("group_name") ? nested.get("group_name") : nested.get("name");
        return name == null ? null : String.valueOf(name);
    }

    private Object groupMember(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        String memberId = required(text(payload, "user_id", "member_openid", "openid"), "成员 openid 不能为空");
        Object result = request(context, HttpMethod.GET, "/v2/groups/" + groupId + "/members/" + memberId, null);
        Map<String, Object> mapped = new LinkedHashMap<>(map(result));
        mapped.putIfAbsent("group_id", groupId);
        mapped.putIfAbsent("user_id", firstNonBlank(text(mapped, "member_openid", "user_openid", "user_id"), memberId));
        return mapped;
    }

    private Object groupMembers(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        List<Map<String, Object>> cached = sessions.groupMembers(context.connectionId(), groupId);
        if (!cached.isEmpty()) {
            return Map.of("members", cached, "cached", true);
        }
        StringBuilder path = new StringBuilder("/v2/groups/").append(groupId).append("/members");
        String start = text(payload, "start_index", "start");
        String limit = text(payload, "limit");
        if (!blank(start) || !blank(limit)) {
            path.append('?');
            if (!blank(start)) {
                path.append("start_index=").append(start);
            }
            if (!blank(limit)) {
                if (!blank(start)) {
                    path.append('&');
                }
                path.append("limit=").append(limit);
            }
        }
        try {
            Object result = request(context, HttpMethod.GET, path.toString(), null);
            List<?> rows = result instanceof List<?> list ? list : listValue(map(result).get("members"));
            for (Object row : rows) {
                Map<String, Object> member = map(row);
                sessions.rememberGroupMember(context.connectionId(), groupId,
                        firstNonBlank(text(member, "member_openid", "user_openid", "user_id", "openid"), null),
                        firstNonBlank(text(member, "nickname", "member_name", "name"), null));
            }
            List<Map<String, Object>> stored = sessions.groupMembers(context.connectionId(), groupId);
            return stored.isEmpty() ? Map.of("members", rows) : Map.of("members", stored);
        } catch (BizException exception) {
            if (officialPermissionDenied(exception)) {
                log.debug("官方群成员接口无权限，回落会话缓存: groupId={}", groupId);
                return Map.of("members", cached, "cached", true);
            }
            throw exception;
        }
    }

    private Object resourceTempUrl(Map<String, Object> payload) {
        String url = firstNonBlank(text(payload, "url", "uri", "file", "temp_url"));
        if (!blank(url) && (url.startsWith("http://") || url.startsWith("https://"))) {
            return Map.of("url", url, "temp_url", url);
        }
        return Map.of();
    }

    private static boolean officialPermissionDenied(BizException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof OfficialHttpException http) {
            return http.status() == 400 || http.status() == 403;
        }
        String message = exception.getMessage();
        return message != null && (message.contains("11253") || message.contains("无接口访问权限"));
    }

    private Object streamPrivateMessage(Context context, Map<String, Object> payload) {
        String peerId = required(text(payload, "user_id", "user_openid", "openid", "peer_id"), "用户 openid 不能为空");
        return request(context, HttpMethod.POST, "/v2/users/" + peerId + "/stream_messages", payload);
    }

    private Object uploadFile(Context context, boolean group, Map<String, Object> payload) {
        String peerId = group
                ? required(text(payload, "group_id", "group_openid", "peer_id"), "群 openid 不能为空")
                : required(text(payload, "user_id", "user_openid", "openid", "peer_id"), "用户 openid 不能为空");
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.remove("group_id");
        body.remove("group_openid");
        body.remove("user_id");
        body.remove("user_openid");
        body.remove("openid");
        body.remove("peer_id");
        if (!body.containsKey("file_type") && payload.get("file_type") == null) {
            body.put("file_type", 1);
        }
        String path = group ? "/v2/groups/" + peerId + "/files" : "/v2/users/" + peerId + "/files";
        return request(context, HttpMethod.POST, path, body);
    }

    private Object kickMembers(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        return request(context, HttpMethod.POST, "/v2/groups/" + groupId + "/batch_remove_members", payload);
    }

    private Object restrictChat(Context context, Map<String, Object> payload, boolean write) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        String path = "/v2/groups/" + groupId + "/restrict_chat_setting";
        return request(context, write ? HttpMethod.POST : HttpMethod.GET, path, write ? officialMuteBody(payload) : null);
    }

    private Map<String, Object> officialMuteBody(Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.remove("group_id");
        body.remove("group_openid");
        body.remove("user_id");
        body.remove("duration");
        body.remove("ban_duration");
        body.remove("time");
        if (payload.get("members") instanceof List<?> existing && !existing.isEmpty()) {
            body.put("members", existing);
            return body;
        }
        String memberId = text(payload, "user_id", "member_openid", "openid");
        if (blank(memberId)) {
            return body;
        }
        long seconds = durationSeconds(payload);
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("member_openid", memberId);
        if (seconds <= 0) {
            member.put("op", "del");
            member.put("mute_expire_at", "");
        } else {
            member.put("op", "add");
            member.put("mute_expire_at", OffsetDateTime.now().plusSeconds(Math.min(seconds, 30L * 24 * 3600)).toString());
        }
        body.put("members", List.of(member));
        return body;
    }

    private static long durationSeconds(Map<String, Object> payload) {
        Object value = payload.get("duration");
        if (value == null) {
            value = payload.get("ban_duration");
        }
        if (value == null) {
            value = payload.get("time");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private Object joinRequests(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        return request(context, HttpMethod.GET, "/v2/groups/" + groupId + "/join_request_list", null);
    }

    private Object approveJoin(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        String memberId = required(text(payload, "user_id", "member_openid", "openid"), "成员 openid 不能为空");
        Map<String, Object> body = new LinkedHashMap<>();
        String op = firstNonBlank(text(payload, "op"));
        if (blank(op)) {
            op = approveFlag(payload) ? "approve" : "decline";
        }
        body.put("op", op);
        String joinRequestId = firstNonBlank(text(payload, "join_request_id", "request_id"));
        if (!blank(joinRequestId)) {
            body.put("join_request_id", joinRequestId);
        }
        String rejectReason = firstNonBlank(text(payload, "reject_reason", "reason"));
        if (!blank(rejectReason) && "decline".equalsIgnoreCase(op)) {
            body.put("reject_reason", rejectReason);
        }
        Object blacklist = payload.get("add_to_member_blacklist");
        if (blacklist instanceof Boolean value) {
            body.put("add_to_member_blacklist", value);
        }
        return request(context, HttpMethod.POST, "/v2/groups/" + groupId + "/approval_join_request/" + memberId, body);
    }

    private static boolean approveFlag(Map<String, Object> payload) {
        Object value = payload.get("approve");
        if (value instanceof Boolean result) {
            return result;
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            String text = String.valueOf(value).trim();
            if ("true".equalsIgnoreCase(text) || "approve".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "decline".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        return "approve".equalsIgnoreCase(text(payload, "op"));
    }

    private Object friendInfo(String userId) {
        if (blank(userId)) {
            throw new BizException("用户 openid 不能为空");
        }
        return Map.of("user_id", userId, "nickname", userId);
    }

    private Object sendChannelMessage(Context context, Map<String, Object> payload) {
        String channelId = required(text(payload, "channel_id", "peer_id"), "子频道 ID 不能为空");
        OfficialMessage message = encode(payload.get("message"), payload);
        OfficialQqBotSessionStore.LastInbound inbound = sessions.lastInbound(context.connectionId(), channelId);
        Map<String, Object> body = new LinkedHashMap<>();
        if (!blank(message.content())) {
            body.put("content", message.content());
        }
        if (message.markdown() != null) {
            body.put("markdown", message.markdown());
        }
        if (message.ark() != null) {
            body.put("ark", message.ark());
        }
        if (message.keyboard() != null) {
            body.put("keyboard", message.keyboard());
        }
        String image = text(payload, "image");
        if (!blank(image)) {
            body.put("image", image);
        }
        String msgId = firstNonBlank(text(payload, "msg_id", "message_id", "message_seq"), inbound == null ? null : inbound.msgId());
        String eventId = firstNonBlank(text(payload, "event_id"), inbound == null ? null : inbound.eventId());
        if (!blank(msgId)) {
            body.put("msg_id", msgId);
        }
        if (!blank(eventId)) {
            body.put("event_id", eventId);
        }
        Object result = request(context, HttpMethod.POST, "/channels/" + channelId + "/messages", body);
        Map<String, Object> mapped = new LinkedHashMap<>(map(result));
        mapped.putIfAbsent("message_seq", firstNonBlank(text(mapped, "id", "msg_id"), msgId));
        mapped.putIfAbsent("message_id", firstNonBlank(text(mapped, "id", "msg_id"), msgId));
        rememberOutbound(context, "channel", channelId, payload, mapped);
        return mapped;
    }

    private Object createGuildDm(Context context, Map<String, Object> payload) {
        String recipientId = required(text(payload, "recipient_id", "user_id", "user_openid"), "接收者 ID 不能为空");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipient_id", recipientId);
        String sourceGuildId = text(payload, "source_guild_id", "guild_id");
        if (!blank(sourceGuildId)) {
            body.put("source_guild_id", sourceGuildId);
        }
        return request(context, HttpMethod.POST, "/users/@me/dms", body);
    }

    private Object sendGuildDm(Context context, Map<String, Object> payload) {
        String guildId = required(text(payload, "guild_id", "peer_id"), "频道私信会话 ID 不能为空");
        OfficialMessage message = encode(payload.get("message"), payload);
        OfficialQqBotSessionStore.LastInbound inbound = sessions.lastInbound(context.connectionId(), guildId);
        Map<String, Object> body = new LinkedHashMap<>();
        if (!blank(message.content())) {
            body.put("content", message.content());
        }
        if (message.markdown() != null) {
            body.put("markdown", message.markdown());
        }
        if (message.ark() != null) {
            body.put("ark", message.ark());
        }
        if (message.keyboard() != null) {
            body.put("keyboard", message.keyboard());
        }
        String msgId = firstNonBlank(text(payload, "msg_id", "message_id", "message_seq"), inbound == null ? null : inbound.msgId());
        String eventId = firstNonBlank(text(payload, "event_id"), inbound == null ? null : inbound.eventId());
        if (!blank(msgId)) {
            body.put("msg_id", msgId);
        }
        if (!blank(eventId)) {
            body.put("event_id", eventId);
        }
        Object result = request(context, HttpMethod.POST, "/dms/" + guildId + "/messages", body);
        Map<String, Object> mapped = new LinkedHashMap<>(map(result));
        mapped.putIfAbsent("message_seq", firstNonBlank(text(mapped, "id", "msg_id"), msgId));
        mapped.putIfAbsent("message_id", firstNonBlank(text(mapped, "id", "msg_id"), msgId));
        rememberOutbound(context, "dm", guildId, payload, mapped);
        return mapped;
    }

    private Object recallChannelMessage(Context context, Map<String, Object> payload) {
        String channelId = required(text(payload, "channel_id", "peer_id"), "子频道 ID 不能为空");
        String messageId = required(text(payload, "message_seq", "message_id", "msg_id", "id"), "消息 ID 不能为空");
        String hidetip = text(payload, "hidetip");
        String path = "/channels/" + channelId + "/messages/" + messageId;
        if (!blank(hidetip)) {
            path += "?hidetip=" + encodeQuery(hidetip);
        }
        return request(context, HttpMethod.DELETE, path, null);
    }

    private Object guildList(Context context, Map<String, Object> payload) {
        StringBuilder path = new StringBuilder("/users/@me/guilds");
        String after = text(payload, "after");
        String before = text(payload, "before");
        String limit = text(payload, "limit");
        List<String> query = new ArrayList<>();
        if (!blank(after)) {
            query.add("after=" + encodeQuery(after));
        }
        if (!blank(before)) {
            query.add("before=" + encodeQuery(before));
        }
        if (!blank(limit)) {
            query.add("limit=" + encodeQuery(limit));
        }
        if (!query.isEmpty()) {
            path.append('?').append(String.join("&", query));
        }
        Object result = request(context, HttpMethod.GET, path.toString(), null);
        List<?> rows = result instanceof List<?> list ? list : listValue(map(result).get("guilds"));
        for (Object row : rows) {
            Map<String, Object> guild = map(row);
            sessions.rememberGuild(context.connectionId(),
                    firstNonBlank(text(guild, "id", "guild_id"), null),
                    firstNonBlank(text(guild, "name", "guild_name"), null));
        }
        return Map.of("guilds", sessions.guildList(context.connectionId()).isEmpty() ? rows : sessions.guildList(context.connectionId()));
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private Object setGuildMute(Context context, Map<String, Object> payload) {
        String guildId = required(text(payload, "guild_id"), "频道 ID 不能为空");
        return request(context, HttpMethod.PATCH, "/guilds/" + guildId + "/mute", guildMuteBody(payload));
    }

    private Object setGuildMemberMute(Context context, Map<String, Object> payload) {
        String guildId = required(text(payload, "guild_id"), "频道 ID 不能为空");
        String userId = required(text(payload, "user_id", "member_id"), "成员 ID 不能为空");
        return request(context, HttpMethod.PATCH, "/guilds/" + guildId + "/members/" + userId + "/mute", guildMuteBody(payload));
    }

    private Object setGuildMembersMute(Context context, Map<String, Object> payload) {
        String guildId = required(text(payload, "guild_id"), "频道 ID 不能为空");
        Map<String, Object> body = guildMuteBody(payload);
        Object userIds = payload.get("user_ids");
        if (userIds != null) {
            body.put("user_ids", userIds);
        }
        return request(context, HttpMethod.PATCH, "/guilds/" + guildId + "/mute", body);
    }

    private Map<String, Object> guildMuteBody(Map<String, Object> payload) {
        Map<String, Object> body = new LinkedHashMap<>();
        String seconds = firstNonBlank(text(payload, "mute_seconds"), durationSeconds(payload) > 0 ? String.valueOf(durationSeconds(payload)) : null);
        String until = text(payload, "mute_end_timestamp");
        if (!blank(until)) {
            body.put("mute_end_timestamp", until);
        }
        if (!blank(seconds)) {
            body.put("mute_seconds", seconds);
        }
        return body;
    }

    private Object groupBotState(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        return request(context, HttpMethod.GET, "/v2/groups/" + groupId + "/bot_state", null);
    }

    private Object joinApprovalStrategy(Context context, Map<String, Object> payload) {
        StringBuilder path = new StringBuilder("/v2/groups/join_approval_strategy");
        String cursor = text(payload, "cursor");
        String limit = text(payload, "limit");
        if (!blank(cursor) || !blank(limit)) {
            path.append('?');
            if (!blank(cursor)) {
                path.append("cursor=").append(encodeQuery(cursor));
            }
            if (!blank(limit)) {
                if (!blank(cursor)) {
                    path.append('&');
                }
                path.append("limit=").append(encodeQuery(limit));
            }
        }
        return request(context, HttpMethod.GET, path.toString(), null);
    }

    private Object sendMessage(Context context, boolean group, Map<String, Object> payload) {
        String peerId = group
                ? required(text(payload, "group_id", "group_openid", "peer_id"), "群 openid 不能为空")
                : required(text(payload, "user_id", "user_openid", "openid", "peer_id"), "用户 openid 不能为空");
        OfficialMessage message = encode(payload.get("message"), payload);
        OfficialQqBotSessionStore.LastInbound inbound = sessions.lastInbound(context.connectionId(), peerId);
        Map<String, Object> media = resolveUploadedMedia(context, group, peerId, message.media());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", message.msgType());
        if (media != null) {
            body.put("media", media);
            // 官方 msg_type=7 必须带非空 content；可见空格/换行会作为 caption 把图片压小。
            // 富媒体独占气泡，忽略调用方传入的说明文字。
            body.put("content", RICH_MEDIA_PLACEHOLDER);
        } else if (!blank(message.content())) {
            body.put("content", message.content());
        }
        if (message.ark() != null) {
            body.put("ark", message.ark());
        }
        if (message.markdown() != null) {
            body.put("markdown", message.markdown());
        }
        if (message.keyboard() != null) {
            body.put("keyboard", message.keyboard());
        }
        String msgId = firstNonBlank(text(payload, "msg_id", "message_id", "message_seq"), inbound == null ? null : inbound.msgId());
        String eventId = firstNonBlank(text(payload, "event_id"), inbound == null ? null : inbound.eventId());
        if (!blank(msgId)) {
            body.put("msg_id", msgId);
        }
        if (!blank(eventId)) {
            body.put("event_id", eventId);
        }
        body.put("msg_seq", sessions.nextMessageSeq(context.connectionId(), peerId));
        String path = group ? "/v2/groups/" + peerId + "/messages" : "/v2/users/" + peerId + "/messages";
        Object result = request(context, HttpMethod.POST, path, body);
        Map<String, Object> mapped = new LinkedHashMap<>(map(result));
        mapped.putIfAbsent("message_seq", firstNonBlank(text(mapped, "id", "msg_id"), msgId));
        mapped.putIfAbsent("message_id", firstNonBlank(text(mapped, "id", "msg_id"), msgId));
        rememberOutbound(context, group ? "group" : "friend", peerId, payload, mapped);
        return mapped;
    }

    private Object history(Context context, Map<String, Object> payload) {
        String scene = firstNonBlank(text(payload, "message_scene", "scene"), "group");
        String peerId = required(text(payload, "peer_id", "group_id", "user_id", "user_openid", "group_openid"), "会话对象不能为空");
        int limit = 20;
        Object rawLimit = payload.get("limit");
        if (rawLimit instanceof Number number) {
            limit = number.intValue();
        } else if (rawLimit != null && !String.valueOf(rawLimit).isBlank()) {
            try {
                limit = Integer.parseInt(String.valueOf(rawLimit));
            } catch (NumberFormatException ignored) {
                limit = 20;
            }
        }
        return Map.of("messages", sessions.history(context.connectionId(), scene, peerId, text(payload, "start_message_seq", "start"), limit));
    }

    private Object message(Context context, Map<String, Object> payload) {
        String messageSeq = required(text(payload, "message_seq", "message_id", "msg_id", "id"), "消息 ID 不能为空");
        Map<String, Object> stored = sessions.message(context.connectionId(), messageSeq);
        if (stored == null) {
            return Map.of();
        }
        return stored;
    }

    private void rememberOutbound(Context context, String scene, String peerId, Map<String, Object> payload, Map<String, Object> result) {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("message_scene", scene);
        stored.put("peer_id", peerId);
        if ("group".equals(scene)) {
            stored.put("group_id", peerId);
        } else if ("channel".equals(scene)) {
            stored.put("channel_id", peerId);
        } else if ("dm".equals(scene)) {
            stored.put("guild_id", peerId);
        } else {
            stored.put("user_id", peerId);
        }
        stored.put("sender_id", firstNonBlank(sessions.selfId(context.connectionId()), context.appId()));
        stored.put("message_seq", firstNonBlank(text(result, "message_seq", "message_id", "id", "msg_id")));
        stored.put("message_id", firstNonBlank(text(result, "message_id", "message_seq", "id", "msg_id")));
        stored.put("time", System.currentTimeMillis() / 1000);
        Object message = payload.get("message");
        if (message instanceof List<?> list) {
            stored.put("segments", list);
        } else {
            String outboundText = message instanceof String value && !value.isBlank()
                    ? value
                    : firstNonBlank(text(payload, "content"), " ");
            stored.put("segments", List.of(Map.of("type", "text", "data", Map.of("text", outboundText))));
        }
        sessions.rememberMessage(context.connectionId(), scene, peerId, stored);
    }

    private Object recall(Context context, Map<String, Object> payload) {
        String groupId = text(payload, "group_id", "group_openid");
        String userId = text(payload, "user_id", "user_openid", "openid");
        String channelId = text(payload, "channel_id");
        String messageId = required(text(payload, "message_seq", "message_id", "msg_id", "id"), "消息 ID 不能为空");
        String path;
        if (!blank(groupId)) {
            path = "/v2/groups/" + groupId + "/messages/" + messageId;
        } else if (!blank(userId)) {
            path = "/v2/users/" + userId + "/messages/" + messageId;
        } else if (!blank(channelId)) {
            path = "/channels/" + channelId + "/messages/" + messageId;
        } else {
            throw new BizException("撤回消息需要群、用户或子频道 ID");
        }
        return request(context, HttpMethod.DELETE, path, null);
    }

    private Object raw(Context context, String api, Map<String, Object> payload) {
        HttpMethod method = HttpMethod.POST;
        String path = api;
        Matcher matcher = HTTP_PREFIX.matcher(api);
        if (matcher.matches()) {
            method = HttpMethod.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
            path = matcher.group(2).trim();
        } else if (payload.containsKey("http_method") || payload.containsKey("method")) {
            String declared = String.valueOf(payload.getOrDefault("http_method", payload.get("method")));
            method = HttpMethod.valueOf(declared.trim().toUpperCase(Locale.ROOT));
            Object rest = payload.get("body");
            payload = rest == null ? payload : map(rest);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return request(context, method, path, payload);
    }

    private Object request(Context context, HttpMethod method, String path, Object body) {
        URI base = base(context);
        long startedAt = System.nanoTime();
        String requestBody;
        try {
            requestBody = body == null ? null : mapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw failure("官方机器人请求 JSON 无法序列化", method, path, base, null, startedAt, exception);
        }
        String response;
        try {
            WebClient client = WebClient.builder().baseUrl(base.toString())
                    .clientConnector(new ReactorClientHttpConnector(OfficialQqBotAccessTokenClient.officialHttpClient().responseTimeout(TIMEOUT)))
                    .defaultHeader(HttpHeaders.AUTHORIZATION, tokens.authorization(context))
                    .defaultHeader("X-Union-Appid", context.appId())
                    .build();
            WebClient.RequestHeadersSpec<?> spec = client.method(method).uri(path);
            if (requestBody != null && method != HttpMethod.GET && method != HttpMethod.DELETE) {
                spec = client.method(method).uri(path).contentType(MediaType.APPLICATION_JSON).bodyValue(requestBody);
            }
            response = spec.retrieve()
                    .onStatus(status -> status.isError(), result -> result.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(content -> Mono.error(new OfficialHttpException(result.statusCode().value(), content))))
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
        } catch (OfficialHttpException exception) {
            if (exception.status() == 401) {
                tokens.invalidate(context.appId());
            }
            throw failure("官方机器人请求失败（HTTP " + exception.status() + "）", method, path, base, exception.status(), startedAt, exception);
        } catch (RuntimeException exception) {
            if (isConnectionReset(exception)) {
                log.warn("Official QQ bot request reset: method={}, path={}, host={}, elapsedMs={}",
                        method, path, base.getHost(), Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            }
            throw failure("官方机器人请求失败", method, path, base, null, startedAt, exception);
        }
        if (response == null || response.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = mapper.readTree(response);
            if (node.has("code") && node.path("code").asInt(0) != 0) {
                throw failure("官方机器人调用失败（code=" + node.path("code").asInt() + "）", method, path, base, null, startedAt, null);
            }
            return mapper.convertValue(node, Object.class);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("官方机器人响应 JSON 无法解析", method, path, base, null, startedAt, exception);
        }
    }

    private OfficialMessage encode(Object message, Map<String, Object> payload) {
        if (payload.containsKey("msg_type") || payload.containsKey("content") || payload.containsKey("media")
                || payload.containsKey("markdown") || payload.containsKey("keyboard") || payload.containsKey("ark")) {
            int msgType = payload.get("msg_type") instanceof Number number ? number.intValue() : 0;
            Map<String, Object> media = mapOrNull(payload.get("media"));
            String content = media != null ? RICH_MEDIA_PLACEHOLDER : text(payload, "content");
            return new OfficialMessage(msgType, content, media,
                    mapOrNull(payload.get("ark")), mapOrNull(payload.get("markdown")), mapOrNull(payload.get("keyboard")));
        }
        List<?> segments = segments(message);
        if (segments.isEmpty() && message instanceof String text) {
            return new OfficialMessage(0, text, null, null, null, null);
        }
        StringBuilder content = new StringBuilder();
        Map<String, Object> media = null;
        int msgType = 0;
        for (Object item : segments) {
            Map<String, Object> segment = map(item);
            String type = String.valueOf(segment.getOrDefault("type", "text"));
            Map<String, Object> data = map(segment.get("data"));
            switch (type) {
                case "text" -> content.append(firstNonBlank(text(data, "text", "content"), ""));
                case "mention", "at" -> content.append("<@").append(firstNonBlank(text(data, "user_id", "qq", "id"), "")).append(">");
                case "image" -> {
                    msgType = 7;
                    media = uploadHint(data, 1);
                }
                case "record", "audio" -> {
                    msgType = 7;
                    media = uploadHint(data, 3);
                }
                case "video" -> {
                    msgType = 7;
                    media = uploadHint(data, 2);
                }
                case "file" -> {
                    msgType = 7;
                    media = uploadHint(data, 4);
                }
                default -> content.append(firstNonBlank(text(data, "text", "content"), ""));
            }
        }
        if (media != null) {
            content.setLength(0);
            content.append(RICH_MEDIA_PLACEHOLDER);
        }
        return new OfficialMessage(msgType, content.toString(), media, null, null, null);
    }

    private Map<String, Object> uploadHint(Map<String, Object> data, int fileType) {
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("file_type", fileType);
        String fileInfo = firstNonBlank(text(data, "file_info", "fileInfo"));
        if (!blank(fileInfo) && !inlineMediaSource(fileInfo)) {
            media.put("file_info", fileInfo);
            return media;
        }
        String source = firstNonBlank(fileInfo, text(data, "uri", "url", "file", "file_data"));
        if (!blank(source)) {
            media.put("url", source);
        }
        return media.size() <= 1 ? null : media;
    }

    /**
     * 官方富媒体必须先 POST /files 拿到 file_info，再随 msg_type=7 被动发出。
     * URL/base64 不能直接塞进 messages.media。
     */
    private Map<String, Object> resolveUploadedMedia(Context context, boolean group, String peerId, Map<String, Object> media) {
        if (media == null || media.isEmpty()) {
            return null;
        }
        String fileInfo = text(media, "file_info", "fileInfo");
        if (!blank(fileInfo) && !inlineMediaSource(fileInfo)) {
            return Map.of("file_info", fileInfo);
        }
        String source = firstNonBlank(fileInfo, text(media, "url", "uri", "file", "file_data"));
        if (blank(source)) {
            throw new BizException("官方机器人图片缺少文件内容");
        }
        Map<String, Object> upload = new LinkedHashMap<>();
        upload.put(group ? "group_id" : "user_id", peerId);
        Object fileType = media.get("file_type");
        int fileTypeValue = fileType instanceof Number number ? number.intValue() : 1;
        upload.put("file_type", fileTypeValue);
        upload.put("srv_send_msg", false);
        // QQ 依 file_name 扩展名识别媒体类型，缺失时客户端可能只显示占位卡片而不展示图片
        upload.put("file_name", mediaFileName(media, source, fileTypeValue));
        if (base64Payload(source) != null) {
            upload.put("file_data", base64Payload(source));
        } else if (source.startsWith("http://") || source.startsWith("https://")) {
            upload.put("url", source);
        } else {
            throw new BizException("官方机器人图片需使用公网 URL 或 base64");
        }
        Object result = uploadFile(context, group, upload);
        Map<String, Object> uploadedResult = map(result);
        String uploaded = firstNonBlank(text(uploadedResult, "file_info", "fileInfo"));
        if (blank(uploaded)) {
            throw new BizException("官方机器人文件上传未返回 file_info");
        }
        log.debug("Official QQ bot media uploaded: connectionId={}, fileType={}, ttl={}",
                context == null ? "" : context.connectionId(), fileTypeValue, text(uploadedResult, "ttl"));
        return Map.of("file_info", uploaded);
    }

    /** 上传文件名：优先沿用调用方命名，URL 取路径末段，base64 嗅探魔数，最后按 file_type 兜底。 */
    private static String mediaFileName(Map<String, Object> media, String source, int fileType) {
        String named = text(media, "file_name", "fileName", "name");
        if (!blank(named)) {
            return named.trim();
        }
        if (source.startsWith("http://") || source.startsWith("https://")) {
            String path = source;
            int query = path.indexOf('?');
            if (query >= 0) {
                path = path.substring(0, query);
            }
            int slash = path.lastIndexOf('/');
            String last = slash >= 0 ? path.substring(slash + 1) : path;
            if (last.contains(".") && !last.startsWith(".") && last.length() <= 64) {
                return last;
            }
        }
        String base64 = base64Payload(source);
        if (base64 != null) {
            String extension = sniffImageExtension(base64);
            if (extension != null) {
                return "image." + extension;
            }
        }
        return switch (fileType) {
            case 2 -> "video.mp4";
            case 3 -> "audio.mp3";
            case 4 -> "file.bin";
            default -> "image.png";
        };
    }

    /** 解码 base64 头部识别常见图片格式，无法识别时返回 null。 */
    private static String sniffImageExtension(String base64) {
        try {
            int length = Math.min(base64.length(), 32);
            length -= length % 4;
            if (length <= 0) {
                return null;
            }
            byte[] head = Base64.getDecoder().decode(base64.substring(0, length));
            if (head.length >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
                return "png";
            }
            if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
                return "jpg";
            }
            if (head.length >= 4 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F' && head[3] == '8') {
                return "gif";
            }
            if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
                return "webp";
            }
            if (head.length >= 2 && head[0] == 'B' && head[1] == 'M') {
                return "bmp";
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static boolean inlineMediaSource(String value) {
        if (blank(value)) {
            return false;
        }
        String source = value.trim();
        return source.startsWith("http://") || source.startsWith("https://")
                || source.startsWith("base64://") || source.startsWith("data:");
    }

    private static String base64Payload(String source) {
        if (blank(source)) {
            return null;
        }
        String value = source.trim();
        if (value.startsWith("base64://")) {
            return value.substring("base64://".length());
        }
        int dataIndex = value.indexOf("base64,");
        if (value.startsWith("data:") && dataIndex > 0) {
            return value.substring(dataIndex + "base64,".length());
        }
        return null;
    }

    private List<?> segments(Object message) {
        if (message instanceof List<?> list) {
            return list;
        }
        if (message instanceof Map<?, ?> map && map.get("segments") instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private URI base(Context context) {
        String value = context == null || blank(context.baseUrl())
                ? (context != null && context.sandbox()
                ? MilkyConnectionProtocol.OFFICIAL_SANDBOX_API
                : MilkyConnectionProtocol.OFFICIAL_API)
                : context.baseUrl();
        try {
            URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) { }
        throw new BizException("官方机器人地址必须是有效的 HTTP 地址");
    }

    private BizException failure(String message, HttpMethod method, String path, URI base, Integer status, long startedAt, Throwable cause) {
        if (status != null && (status == 400 || status == 403 || status == 404)) {
            log.warn("Official QQ bot request failed: method={}, path={}, host={}, status={}, elapsedMs={}",
                    method, path, base.getHost(), status, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        } else if (isConnectionReset(cause)) {
            log.warn("Official QQ bot request failed: method={}, path={}, host={}, status={}, elapsedMs={}",
                    method, path, base.getHost(), status, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        } else {
            log.error("Official QQ bot request failed: method={}, path={}, host={}, status={}, elapsedMs={}",
                    method, path, base.getHost(), status, Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), cause);
        }
        BizException exception = new BizException(message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
    }

    static boolean isConnectionReset(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof java.net.SocketException && String.valueOf(current.getMessage()).contains("Connection reset")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> copied = new LinkedHashMap<>();
            raw.forEach((key, item) -> copied.put(String.valueOf(key), item));
            return copied;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> mapOrNull(Object value) {
        return value == null ? null : map(value);
    }

    private static String text(Map<String, Object> payload, String... keys) {
        if (payload == null) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
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

    private static String required(String value, String message) {
        if (blank(value)) {
            throw new BizException(message);
        }
        return value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record OfficialMessage(int msgType, String content, Map<String, Object> media,
                                   Map<String, Object> ark, Map<String, Object> markdown, Map<String, Object> keyboard) { }

    private static final class OfficialHttpException extends RuntimeException {
        private final int status;

        private OfficialHttpException(int status, String body) {
            super(body);
            this.status = status;
        }

        private int status() {
            return status;
        }
    }
}
