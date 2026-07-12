package online.yudream.base.application.platform.milky.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class MilkyChatAppService {
    private final MilkyConnectionRepo connectionRepo;
    private final MilkyApiGateway apiGateway;
    private final Map<Long, CopyOnWriteArrayList<Consumer<MilkyModels.Event>>> subscribers = new ConcurrentHashMap<>();

    public AutoCloseable subscribe(Long connectionId, Consumer<MilkyModels.Event> consumer) {
        var list = subscribers.computeIfAbsent(connectionId, ignored -> new CopyOnWriteArrayList<>());
        list.add(consumer);
        return () -> {
            list.remove(consumer);
            if (list.isEmpty()) subscribers.remove(connectionId, list);
        };
    }

    public void publishEvent(Long connectionId, MilkyModels.Event event) {
        subscribers.getOrDefault(connectionId, new CopyOnWriteArrayList<>()).forEach(consumer -> {
            try { consumer.accept(event); } catch (RuntimeException ignored) { }
        });
    }

    @Transactional(readOnly = true)
    public Object conversations(Long id) {
        MilkyConnection connection = connection(id);
        return Map.of("groups", call(connection, "get_group_list", Map.of()), "friends", call(connection, "get_friend_list", Map.of()));
    }

    @Transactional(readOnly = true)
    public Object history(Long id, String scene, String peerId, String start, int limit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message_scene", scene);
        body.put("peer_id", peerId);
        if (start != null && !start.isBlank()) {
            body.put("start_message_seq", start);
        }
        body.put("limit", Math.min(30, Math.max(1, limit)));
        return call(connection(id), "get_history_messages", body);
    }

    @Transactional
    public Object send(Long id, String scene, String peerId, Object message) {
        if (!"group".equals(scene) && !"friend".equals(scene) && !"private".equals(scene)) {
            throw new BizException("不支持的会话类型");
        }
        if (peerId == null || peerId.isBlank()) {
            throw new BizException("会话对象不能为空");
        }
        String api = "group".equals(scene) ? "send_group_message" : "send_private_message";
        return call(connection(id), api, Map.of("group".equals(scene) ? "group_id" : "user_id", peerId, "message", message));
    }

    /** Exposes every documented Milky API through the selected connection. */
    @Transactional(readOnly = true)
    public Object invoke(Long id, String api, Object payload) {
        if (api == null || !api.matches("[a-z][a-z0-9_]{0,127}")) {
            throw new BizException("Milky API 名称无效");
        }
        return call(connection(id), api, payload == null ? Map.of() : payload);
    }

    private Object call(MilkyConnection connection, String api, Object body) {
        return apiGateway.invoke(new MilkyModels.Context(connection.getBaseUrl(), connection.getToken(), null), api, body);
    }

    private MilkyConnection connection(Long id) {
        MilkyConnection connection = connectionRepo.findById(id).orElseThrow(() -> new BizException("Milky 连接不存在"));
        if (!connection.isEnabled()) {
            throw new BizException("Milky 连接未启用");
        }
        return connection;
    }
}
