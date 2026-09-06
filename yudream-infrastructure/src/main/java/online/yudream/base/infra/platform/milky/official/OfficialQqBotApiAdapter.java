package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.enumerate.MilkyConnectionProtocol;
import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Duration;
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
            case "get_group_list" -> Map.of("groups", sessions.groupList(context.connectionId()));
            case "get_friend_list" -> Map.of("friends", sessions.friendList(context.connectionId()));
            case "get_group_info" -> groupInfo(context, payload);
            case "get_group_member_info" -> groupMember(context, payload);
            case "get_group_member_list" -> groupMembers(context, payload);
            case "get_friend_info" -> friendInfo(text(payload, "user_id", "user_openid", "openid"));
            case "get_history_messages", "get_message", "get_forwarded_messages" ->
                    unsupported("官方机器人不提供历史消息拉取，请使用事件流");
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
            default -> raw(context, method, payload);
        };
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

    private Object groupInfo(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        Object result = request(context, HttpMethod.GET, "/v2/groups/" + groupId + "/info", null);
        Map<String, Object> mapped = new LinkedHashMap<>(map(result));
        mapped.putIfAbsent("group_id", firstNonBlank(text(mapped, "group_openid", "group_id"), groupId));
        mapped.putIfAbsent("group_name", firstNonBlank(text(mapped, "group_name"), groupId));
        sessions.rememberGroup(context.connectionId(), groupId, text(mapped, "group_name"));
        return mapped;
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
        return request(context, HttpMethod.GET, path.toString(), null);
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
        return request(context, write ? HttpMethod.POST : HttpMethod.GET, path, write ? payload : null);
    }

    private Object joinRequests(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        return request(context, HttpMethod.GET, "/v2/groups/" + groupId + "/join_request_list", null);
    }

    private Object approveJoin(Context context, Map<String, Object> payload) {
        String groupId = required(text(payload, "group_id", "group_openid"), "群 openid 不能为空");
        String memberId = required(text(payload, "user_id", "member_openid", "openid"), "成员 openid 不能为空");
        return request(context, HttpMethod.POST, "/v2/groups/" + groupId + "/approval_join_request/" + memberId, payload);
    }

    private Object friendInfo(String userId) {
        if (blank(userId)) {
            throw new BizException("用户 openid 不能为空");
        }
        return Map.of("user_id", userId, "nickname", userId);
    }

    private Object sendMessage(Context context, boolean group, Map<String, Object> payload) {
        String peerId = group
                ? required(text(payload, "group_id", "group_openid", "peer_id"), "群 openid 不能为空")
                : required(text(payload, "user_id", "user_openid", "openid", "peer_id"), "用户 openid 不能为空");
        OfficialMessage message = encode(payload.get("message"), payload);
        OfficialQqBotSessionStore.LastInbound inbound = sessions.lastInbound(context.connectionId(), peerId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("msg_type", message.msgType());
        if (!blank(message.content())) {
            body.put("content", message.content());
        }
        if (message.media() != null) {
            body.put("media", message.media());
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
        return mapped;
    }

    private Object recall(Context context, Map<String, Object> payload) {
        String groupId = text(payload, "group_id", "group_openid");
        String userId = text(payload, "user_id", "user_openid", "openid");
        String messageId = required(text(payload, "message_seq", "message_id", "msg_id", "id"), "消息 ID 不能为空");
        String path;
        if (!blank(groupId)) {
            path = "/v2/groups/" + groupId + "/messages/" + messageId;
        } else if (!blank(userId)) {
            path = "/v2/users/" + userId + "/messages/" + messageId;
        } else {
            throw new BizException("撤回消息需要群或用户 openid");
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
                    .clientConnector(new ReactorClientHttpConnector(HttpClient.create().responseTimeout(TIMEOUT)))
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
        if (payload.containsKey("msg_type") || payload.containsKey("content") || payload.containsKey("media")) {
            int msgType = payload.get("msg_type") instanceof Number number ? number.intValue() : 0;
            return new OfficialMessage(msgType, text(payload, "content"), mapOrNull(payload.get("media")),
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
        if (media != null && content.isEmpty()) {
            content.append(" ");
        }
        return new OfficialMessage(msgType, content.toString(), media, null, null, null);
    }

    private Map<String, Object> uploadHint(Map<String, Object> data, int fileType) {
        Map<String, Object> media = new LinkedHashMap<>();
        String fileInfo = firstNonBlank(text(data, "file_info", "fileInfo"));
        if (!blank(fileInfo)) {
            media.put("file_info", fileInfo);
            return media;
        }
        String url = firstNonBlank(text(data, "uri", "url", "file"));
        if (!blank(url)) {
            media.put("url", url);
            media.put("file_type", fileType);
        }
        return media.isEmpty() ? null : media;
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

    private Object unsupported(String message) {
        throw new BizException(message);
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
        log.error("Official QQ bot request failed: method={}, path={}, host={}, status={}, elapsedMs={}",
                method, path, base.getHost(), status, Duration.ofNanos(System.nanoTime() - startedAt).toMillis(), cause);
        BizException exception = new BizException(message);
        if (cause != null) {
            exception.initCause(cause);
        }
        return exception;
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
