package online.yudream.base.application.platform.milky.sandbox.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.sandbox.assembler.QqSandboxAssembler;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxCreateCmd;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxSessionDTO;
import online.yudream.base.application.platform.milky.sandbox.port.QqSandboxRuntimeGateway;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxTimelineEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class QqSandboxAppService {
    private final QqSandboxSessionRepo sessions;
    private final QqSandboxRuntimeGateway runtime;
    private MilkyConnectionRepo milkyConnections;

    @Autowired
    void setMilkyConnections(MilkyConnectionRepo milkyConnections) {
        this.milkyConnections = milkyConnections;
    }

    public QqSandboxSessionDTO create(QqSandboxCreateCmd cmd) {
        ensurePolicyConnection(cmd.policyConnectionId());
        long timeout = cmd.timeoutMillis() == null ? 120_000L : cmd.timeoutMillis();
        String sessionId = UUID.randomUUID().toString();
        QqSandboxSession session = QqSandboxSession.create(sessionId, cmd.pluginCode(), cmd.policyConnectionId(),
                cmd.selfId(), cmd.userId(), cmd.nickname(), cmd.channelId(), cmd.scene(), cmd.randomMode(), timeout,
                Instant.now());
        session.append("session", "session.created", cmd.pluginCode(), Map.of(
                "randomMode", session.randomMode().name(), "connectionId", session.connectionId()));
        sessions.save(session);
        return QqSandboxAssembler.toDTO(session);
    }

    public QqSandboxSessionDTO send(String sessionId, QqSandboxMessageCmd cmd) {
        QqSandboxSession session = session(sessionId);
        if (cmd == null || cmd.content() == null || cmd.content().isBlank()) {
            throw new BizException("QQ 沙箱消息不能为空");
        }
        session.running();
        session.append("input", "message.synthetic", session.pluginCode(), inputPayload(session, cmd));
        java.util.concurrent.CompletableFuture<Void> execution = runtime.dispatch(session, cmd).toCompletableFuture();
        try {
            execution.get(session.timeoutMillis(), TimeUnit.MILLISECONDS);
            session.completed();
            session.append("session", "dispatch.completed", session.pluginCode(), Map.of());
        } catch (TimeoutException error) {
            execution.cancel(true);
            runtime.cancel(session);
            session.timedOut();
            session.append("session", "dispatch.timeout", session.pluginCode(),
                    Map.of("timeoutMillis", session.timeoutMillis()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            session.failed();
            throw new BizException("QQ 沙箱执行被中断");
        } catch (Exception error) {
            session.failed();
            String message = error.getCause() == null ? error.getMessage() : error.getCause().getMessage();
            session.append("session", "dispatch.failed", session.pluginCode(),
                    Map.of("message", message == null ? error.getClass().getSimpleName() : message));
        }
        sessions.save(session);
        return QqSandboxAssembler.toDTO(session);
    }

    public List<QqSandboxSessionDTO> list() {
        return sessions.findAll().stream().map(QqSandboxAssembler::toDTO).toList();
    }

    public QqSandboxSessionDTO detail(String id) {
        return QqSandboxAssembler.toDTO(session(id));
    }

    public AutoCloseable subscribe(String id, Consumer<QqSandboxTimelineEvent> listener) {
        return session(id).subscribe(listener);
    }

    public void delete(String id) {
        QqSandboxSession session = session(id);
        session.close();
        sessions.delete(id);
    }

    private Map<String, Object> inputPayload(QqSandboxSession session, QqSandboxMessageCmd cmd) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("senderId", senderId(session, cmd));
        payload.put("nickname", cmd.nickname() == null ? "" : cmd.nickname());
        payload.put("content", cmd.content());
        payload.put("mentionSelf", cmd.mentionSelf());
        payload.put("mentions", cmd.mentions());
        payload.put("replyMessageId", cmd.replyMessageId() == null ? "" : cmd.replyMessageId());
        payload.put("clientMessageId", cmd.clientMessageId() == null ? "" : cmd.clientMessageId());
        return payload;
    }

    private String senderId(QqSandboxSession session, QqSandboxMessageCmd cmd) {
        return cmd.senderId() == null || cmd.senderId().isBlank() ? session.userId() : cmd.senderId().trim();
    }

    private void ensurePolicyConnection(String id) {
        if (milkyConnections == null) return;
        Long connectionId;
        try {
            connectionId = Long.valueOf(id);
        } catch (RuntimeException error) {
            throw new BizException("QQ 沙箱策略连接 ID 无效");
        }
        MilkyConnection connection = milkyConnections.findById(connectionId)
                .orElseThrow(() -> new BizException("QQ 沙箱策略连接不存在"));
        if (!connection.isEnabled()) throw new BizException("QQ 沙箱策略连接未启用");
    }

    private QqSandboxSession session(String id) {
        return sessions.findById(id).orElseThrow(() -> new BizException("QQ 沙箱会话不存在"));
    }
}
