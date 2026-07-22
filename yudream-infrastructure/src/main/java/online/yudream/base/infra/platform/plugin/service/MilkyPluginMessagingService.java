package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageResult;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingConnection;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingGroup;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingRawService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Service
@RequiredArgsConstructor
public class MilkyPluginMessagingService implements PluginMessagingService, PluginMessagingRawService {
    private final MilkyConnectionRepo connectionRepo;
    private final MilkyApiGateway apiGateway;
    private final PluginUserService pluginUserService;
    private final ObjectMapper objectMapper;

    @Override
    public List<PluginMessagingConnection> connections() {
        return connectionRepo.findEnabled().stream()
                // A picker must only read local connection configuration. Calling get_login_info
                // here turns every plugin page load into a potentially long remote Milky request.
                .map(connection -> new PluginMessagingConnection(String.valueOf(connection.getId()), connection.getName(), "qq", null))
                .toList();
    }

    @Override
    public List<PluginMessagingGroup> groups(String connectionId) {
        Object data = apiGateway.invoke(context(connection(connectionId)), "get_group_list", Map.of());
        Object rowsValue = groupRows(data);
        if (!(rowsValue instanceof Iterable<?> rows)) return List.of();
        List<PluginMessagingGroup> groups = new java.util.ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> value)) continue;
            Object id = value.containsKey("group_id") ? value.get("group_id") : value.containsKey("group_uin") ? value.get("group_uin") : value.get("id");
            if (id == null) continue;
            Object name = value.containsKey("group_name") ? value.get("group_name") : value.get("name");
            groups.add(new PluginMessagingGroup(String.valueOf(id), name == null ? String.valueOf(id) : String.valueOf(name)));
        }
        return List.copyOf(groups);
    }

    private Object groupRows(Object value) {
        if (!(value instanceof Map<?, ?> map)) return value;
        for (String key : List.of("groups", "group_list", "list", "data")) {
            if (map.containsKey(key)) return groupRows(map.get(key));
        }
        return value;
    }

    @Override
    public CompletionStage<PluginMessageResult> send(PluginMessageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (request == null || request.content() == null) {
                throw new BizException("插件消息请求不能为空");
            }
            return sendNow(connection(request.connectionId()), request.channelId(), "group", request.content());
        });
    }

    @Override
    public CompletionStage<PluginMessageResult> sendDirectToBoundUser(String userId, PluginMessageContent content) {
        return CompletableFuture.supplyAsync(() -> {
            if (content == null) {
                throw new BizException("私聊内容不能为空");
            }
            Long systemUserId;
            try {
                systemUserId = Long.valueOf(userId);
            } catch (RuntimeException exception) {
                throw new BizException("系统用户 ID 无效");
            }
            String qq = pluginUserService.findById(systemUserId)
                    .map(profile -> profile.qq())
                    .filter(value -> value != null && !value.isBlank())
                    .orElseThrow(() -> new BizException("用户尚未绑定 QQ"));
            List<MilkyConnection> connections = connectionRepo.findEnabled();
            if (connections.size() != 1) {
                throw new BizException("私聊需要恰好一个已启用的 Milky 连接");
            }
            return sendNow(connections.getFirst(), qq.trim(), "private", content);
        });
    }

    @Override
    public CompletionStage<PluginMessageResult> sendToChannel(String connectionId, String channelId, PluginMessageContent content) {
        return CompletableFuture.supplyAsync(() -> sendNow(connection(connectionId), channelId, "group", content));
    }

    @Override
    public CompletionStage<Map<String, Object>> invoke(String connectionId, String method, Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> map(apiGateway.invoke(context(connection(connectionId)), method, payload == null ? Map.of() : payload)));
    }

    private PluginMessageResult sendNow(MilkyConnection connection, String peer, String scene, PluginMessageContent content) {
        if (peer == null || peer.isBlank()) {
            throw new BizException("消息目标不能为空");
        }
        String api = "group".equals(scene) ? "send_group_message" : "send_private_message";
        String idKey = "group".equals(scene) ? "group_id" : "user_id";
        Map<String, Object> segment = switch (content.type()) {
            case IMAGE -> Map.of("type", "image", "data", Map.of("uri", content.content()));
            case AUDIO -> Map.of("type", "record", "data", Map.of("uri", content.content()));
            case VIDEO -> Map.of("type", "video", "data", Map.of("uri", content.content()));
            case FILE -> Map.of("type", "file", "data", Map.of("uri", content.content()));
            case COMPOSITE -> Map.of("type", "forward", "data", compositeData(content.content()));
            default -> Map.of("type", "text", "data", Map.of("text", content.content()));
        };
        List<Map<String, Object>> message = List.of(segment);
        Map<String, Object> result = map(apiGateway.invoke(context(connection), api, Map.of(idKey, peer, "message", message)));
        return new PluginMessageResult(List.of(String.valueOf(result.getOrDefault("message_seq", ""))), false, false);
    }

    private Map<String, Object> compositeData(String content) {
        try {
            Map<String, Object> data = objectMapper.readValue(content, new TypeReference<>() { });
            if (!data.containsKey("messages")) {
                throw new BizException("Composite plugin message must contain forward messages");
            }
            return data;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("Composite plugin message is not valid JSON");
        }
    }

    private MilkyConnection connection(String id) {
        try {
            return connectionRepo.findById(Long.valueOf(id)).filter(MilkyConnection::isEnabled)
                    .orElseThrow(() -> new BizException("Milky 连接不存在或未启用"));
        } catch (NumberFormatException exception) {
            throw new BizException("Milky 连接 ID 无效");
        }
    }

    private MilkyModels.Context context(MilkyConnection connection) {
        return new MilkyModels.Context(connection.getBaseUrl(), connection.getToken(), null);
    }

    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> copied = new LinkedHashMap<>();
            raw.forEach((key, item) -> copied.put(String.valueOf(key), item));
            return Collections.unmodifiableMap(copied);
        }
        return value == null ? Map.of() : Map.of("data", value);
    }
}
