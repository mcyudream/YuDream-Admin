package online.yudream.base.interfaces.platform.milky.assembler;

import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxCreateCmd;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxConnectionOptionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxGroupsDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxRoleOptionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxSenderOptionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxSessionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxTimelineEventDTO;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxRandomMode;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxTimelineEvent;
import online.yudream.base.interfaces.platform.milky.request.QqSandboxCreateRequest;
import online.yudream.base.interfaces.platform.milky.request.QqSandboxMessageRequest;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxConnectionOptionRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxEventRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxGroupOptionRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxGroupsRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxMessageRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxPresetRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxPresetsRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxRoleOptionRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxSenderOptionRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxSessionRes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class QqSandboxWebAssembler {
    private QqSandboxWebAssembler() {
    }

    public static QqSandboxCreateCmd toCmd(QqSandboxCreateRequest request) {
        boolean group = "GROUP".equalsIgnoreCase(request.conversationType());
        String botId = textOrDefault(request.botId(), "10000");
        String channelId = group ? requireGroupId(request.groupId()) : request.userId();
        return new QqSandboxCreateCmd(request.pluginCode(), request.policyConnectionId(), botId, request.userId(),
                request.nickname(), channelId,
                group ? "group" : "private", randomMode(request),
                Boolean.TRUE.equals(request.forceUnbound()), request.simulateRoles(), 120_000L);
    }

    public static QqSandboxMessageCmd toCmd(QqSandboxMessageRequest request) {
        return new QqSandboxMessageCmd(request.senderId(), request.nickname(), request.content(), request.mentionSelf(),
                request.mentions(), request.replyMessageId(), request.clientMessageId());
    }

    public static QqSandboxSessionRes toRes(QqSandboxSessionDTO dto) {
        Map<String, Object> metadata = Map.of(
                "connectionId", dto.connectionId(),
                "policyConnectionId", dto.policyConnectionId(),
                "channelId", dto.channelId(),
                "randomMode", dto.randomMode().name(),
                "timeoutMillis", dto.timeoutMillis());
        return new QqSandboxSessionRes(dto.id(), webStatus(dto.status()), conversationType(dto.scene()),
                dto.pluginCode(), dto.policyConnectionId(), dto.selfId(), dto.userId(),
                "group".equals(dto.scene()) ? dto.channelId() : null,
                dto.nickname(), dto.randomMode().name(), dto.createdAt(), null, metadata);
    }

    public static QqSandboxMessageRes toMessageRes(QqSandboxSessionDTO dto, QqSandboxMessageRequest request) {
        String senderId = textOrDefault(request.senderId(), dto.userId());
        String messageId = textOrDefault(request.clientMessageId(), "sandbox-" + dto.timeline().size());
        return new QqSandboxMessageRes(messageId, dto.id(), "INBOUND", senderId, request.nickname(),
                "TEXT", request.content(), Instant.now(), Map.of(
                "status", dto.status(), "randomMode", dto.randomMode().name()));
    }

    public static QqSandboxEventRes toEventRes(QqSandboxTimelineEventDTO event, String sessionId) {
        return new QqSandboxEventRes("sandbox.timeline", event.action(), "qq-sandbox", sessionId,
                event.timestamp(), event.payload());
    }

    public static QqSandboxEventRes toEventRes(QqSandboxTimelineEvent event, String sessionId) {
        return new QqSandboxEventRes("sandbox.timeline", event.action(), "qq-sandbox", sessionId,
                event.timestamp(), event.payload());
    }

    public static QqSandboxPresetsRes presets(List<QqSandboxConnectionOptionDTO> connections,
                                              List<QqSandboxSenderOptionDTO> senders,
                                              List<QqSandboxRoleOptionDTO> roles) {
        List<QqSandboxConnectionOptionRes> connectionOptions = connections == null ? List.of()
                : connections.stream()
                .map(item -> new QqSandboxConnectionOptionRes(item.connectionId(), item.name()))
                .toList();
        List<QqSandboxSenderOptionRes> senderOptions = senders == null ? List.of()
                : senders.stream()
                .map(item -> new QqSandboxSenderOptionRes(item.qq(), item.nickname(), item.userId(), item.roles()))
                .toList();
        List<QqSandboxRoleOptionRes> roleOptions = roles == null ? List.of()
                : roles.stream()
                .map(item -> new QqSandboxRoleOptionRes(item.code(), item.name()))
                .toList();
        return new QqSandboxPresetsRes(List.of(
                preset("group-real", "群聊真实随机", "GROUP", "REAL", "/帮助"),
                preset("group-force-hit", "群聊强制触发", "GROUP", "FORCE_HIT", "@机器人 你好"),
                preset("group-force-miss", "群聊强制不触发", "GROUP", "FORCE_MISS", "普通群聊消息"),
                preset("private-real", "私聊真实随机", "PRIVATE", "REAL", "/帮助")), connectionOptions, senderOptions, roleOptions);
    }

    public static QqSandboxGroupsRes groups(QqSandboxGroupsDTO dto) {
        List<QqSandboxGroupOptionRes> groupOptions = dto.groups() == null ? List.of()
                : dto.groups().stream()
                .map(item -> new QqSandboxGroupOptionRes(item.groupId(), item.groupName()))
                .toList();
        return new QqSandboxGroupsRes(dto.selfId(), groupOptions);
    }

    private static QqSandboxPresetRes preset(String code, String name, String conversationType,
                                             String randomMode, String content) {
        // 策略连接必须指向真实已启用的 Milky 连接，预设不猜测，由使用者显式填写
        return new QqSandboxPresetRes(code, name, "模拟插件 QQ 消息", conversationType, null,
                null, "10000", "10001", "GROUP".equals(conversationType) ? "20001" : null,
                "沙箱用户", null, content, Map.of("randomMode", randomMode));
    }

    private static QqSandboxRandomMode randomMode(QqSandboxCreateRequest request) {
        if (request.randomMode() != null && !request.randomMode().isBlank()) {
            return QqSandboxRandomMode.from(request.randomMode());
        }
        Object metadataMode = request.metadata() == null ? null : request.metadata().get("randomMode");
        return QqSandboxRandomMode.from(metadataMode == null ? null : String.valueOf(metadataMode));
    }

    private static String conversationType(String scene) {
        return "group".equals(scene) ? "GROUP" : "PRIVATE";
    }

    private static String webStatus(String status) {
        return switch (status) {
            case "READY" -> "CREATED";
            case "RUNNING" -> "CONNECTED";
            case "FAILED", "TIMED_OUT" -> "ERROR";
            default -> status;
        };
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String requireGroupId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("群聊沙箱必须提供群 ID");
        return value.trim();
    }
}
