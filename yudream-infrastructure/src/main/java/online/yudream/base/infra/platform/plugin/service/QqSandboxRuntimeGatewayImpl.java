package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.application.platform.milky.sandbox.port.QqSandboxRuntimeGateway;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@Service
@RequiredArgsConstructor
public class QqSandboxRuntimeGatewayImpl implements QqSandboxRuntimeGateway {
    private final MilkyPluginEventDispatcher dispatcher;
    private final JarPluginRuntimeGateway pluginRuntime;

    @Override
    public CompletionStage<Void> dispatch(QqSandboxSession session, QqSandboxMessageCmd message) {
        // 空插件范围表示对标真实群聊广播给全部已启用插件，无需做单插件启用校验
        if (!session.pluginCode().isEmpty() && !pluginRuntime.enabled(session.pluginCode())) {
            throw new BizException("QQ 沙箱目标插件不存在或未启用");
        }
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(session)) {
            Map<String, Object> data = eventData(session, message);
            String eventType = switch (message.type()) {
                case "group_request" -> "group_request";
                case "button" -> "button_click";
                default -> "message_receive";
            };
            String messageSeq = String.valueOf(data.get("message_seq"));
            MilkyModels.Event event = new MilkyModels.Event(Instant.now().getEpochSecond(), session.selfId(),
                    eventType, data);
            session.append("runtime", "milky.dispatch", session.pluginCode(), Map.of(
                    "eventType", event.eventType(), "messageSeq", messageSeq,
                    "connectionId", session.connectionId(), "randomMode", session.randomMode().name()));
            dispatcher.dispatchSandbox(session.connectionId(), event, sandboxReferrer(session));
            return QqSandboxExecutionScope.awaitTracked();
        }
    }

    @Override
    public void cancel(QqSandboxSession session) {
        QqSandboxExecutionScope.cancelPending(session);
    }

    static Map<String, Object> sandboxReferrer(QqSandboxSession session) {
        return Map.of(
                "sandboxSessionId", session.id(),
                "sandboxRandomMode", session.randomMode().name(),
                "sandboxPolicyConnectionId", session.policyConnectionId());
    }

    static Map<String, Object> eventData(QqSandboxSession session, QqSandboxMessageCmd message) {
        return switch (message.type()) {
            case "group_request" -> groupRequestData(session, message);
            case "button" -> buttonClickData(session, message);
            default -> messageData(session, message);
        };
    }

    /** 入群请求事件载荷，字段名对齐生产 dispatchGroupRequest 解析的 group_id/user_id/request_id/comment */
    private static Map<String, Object> groupRequestData(QqSandboxSession session, QqSandboxMessageCmd message) {
        String senderId = textOrDefault(message.senderId(), session.userId());
        String requestId = textOrDefault(message.clientMessageId(), UUID.randomUUID().toString());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("group_id", session.channelId());
        data.put("user_id", senderId);
        data.put("sender_id", senderId);
        data.put("request_id", requestId);
        data.put("message_seq", requestId);
        if (message.content() != null && !message.content().isBlank()) {
            data.put("comment", message.content().trim());
        }
        return Map.copyOf(data);
    }

    /** 按钮回调事件载荷，button_id 驱动插件 onButton 交互 */
    private static Map<String, Object> buttonClickData(QqSandboxSession session, QqSandboxMessageCmd message) {
        String senderId = textOrDefault(message.senderId(), session.userId());
        String messageSeq = textOrDefault(message.clientMessageId(), UUID.randomUUID().toString());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("button_id", message.buttonId().trim());
        data.put("sender_id", senderId);
        data.put("user_id", senderId);
        data.put("peer_id", session.channelId());
        if ("group".equals(session.scene())) data.put("group_id", session.channelId());
        data.put("message_seq", messageSeq);
        return Map.copyOf(data);
    }

    private static Map<String, Object> messageData(QqSandboxSession session, QqSandboxMessageCmd message) {
        String senderId = textOrDefault(message.senderId(), session.userId());
        String messageSeq = textOrDefault(message.clientMessageId(), UUID.randomUUID().toString());
        List<Map<String, Object>> segments = segments(session, message);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_scene", session.scene());
        data.put("peer_id", session.channelId());
        data.put("sender_id", senderId);
        data.put("user_id", senderId);
        data.put("sender_name", textOrDefault(message.nickname(), senderId));
        data.put("sender_nickname", textOrDefault(message.nickname(), senderId));
        data.put("nickname", textOrDefault(message.nickname(), senderId));
        if ("group".equals(session.scene())) data.put("group_id", session.channelId());
        data.put("message_seq", messageSeq);
        data.put("client_message_id", messageSeq);
        data.put("message", segments);
        data.put("segments", segments);
        data.put("raw_message", message.content());
        return Map.copyOf(data);
    }

    private static List<Map<String, Object>> segments(QqSandboxSession session, QqSandboxMessageCmd message) {
        List<Map<String, Object>> segments = new ArrayList<>();
        segments.add(Map.of("type", "text", "data", Map.of("text", message.content())));
        LinkedHashSet<String> mentions = new LinkedHashSet<>();
        if (message.mentionSelf()) mentions.add(session.selfId());
        message.mentions().stream().filter(value -> value != null && !value.isBlank()).map(String::trim)
                .forEach(mentions::add);
        mentions.forEach(userId -> segments.add(Map.of("type", "mention", "data", Map.of(
                "user_id", userId, "qq", userId))));
        if (message.replyMessageId() != null && !message.replyMessageId().isBlank()) {
            String replyId = message.replyMessageId().trim();
            segments.add(Map.of("type", "reply", "data", Map.of(
                    "message_id", replyId, "message_seq", replyId)));
        }
        return List.copyOf(segments);
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
