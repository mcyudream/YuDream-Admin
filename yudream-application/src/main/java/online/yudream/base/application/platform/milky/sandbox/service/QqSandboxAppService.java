package online.yudream.base.application.platform.milky.sandbox.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.sandbox.assembler.QqSandboxAssembler;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxCaseSaveCmd;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxCreateCmd;
import online.yudream.base.application.platform.milky.sandbox.cmd.QqSandboxMessageCmd;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxCaseDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxConnectionOptionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxGroupOptionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxGroupsDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxRoleOptionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxSenderOptionDTO;
import online.yudream.base.application.platform.milky.sandbox.dto.QqSandboxSessionDTO;
import online.yudream.base.application.platform.milky.sandbox.port.QqSandboxRuntimeGateway;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCase;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxCaseStep;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxTimelineEvent;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.RoleID;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QqSandboxAppService {
    private final QqSandboxSessionRepo sessions;
    private final QqSandboxCaseRepo caseRepo;
    private final QqSandboxRuntimeGateway runtime;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final MilkyApiGateway milkyApiGateway;
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
                cmd.selfId(), cmd.userId(), cmd.nickname(), cmd.channelId(), cmd.scene(), cmd.randomMode(),
                Boolean.TRUE.equals(cmd.forceUnbound()), cmd.simulateRoles(), timeout, Instant.now());
        Map<String, Object> createdPayload = new LinkedHashMap<>();
        createdPayload.put("randomMode", session.randomMode().name());
        createdPayload.put("connectionId", session.connectionId());
        createdPayload.put("forceUnbound", session.forceUnbound());
        createdPayload.put("simulateRoles", session.simulateRoles() == null ? "REAL" : session.simulateRoles());
        session.append("session", "session.created", session.pluginCode(), createdPayload);
        sessions.save(session);
        return QqSandboxAssembler.toDTO(session);
    }

    public QqSandboxSessionDTO send(String sessionId, QqSandboxMessageCmd cmd) {
        QqSandboxSession session = session(sessionId);
        validateMessage(cmd);
        session.running();
        session.append("input", syntheticAction(cmd), session.pluginCode(), inputPayload(session, cmd));
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

    /**
     * 沙盒策略连接只接受已启用的真实 Milky 连接，此处提供可选项供前端自动带出，免除手输 ID
     */
    public List<QqSandboxConnectionOptionDTO> connectionOptions() {
        if (milkyConnections == null) return List.of();
        return milkyConnections.findEnabled().stream()
                .map(connection -> new QqSandboxConnectionOptionDTO(String.valueOf(connection.getId()), connection.getName()))
                .toList();
    }

    /**
     * 发送人/提及人可选项：已填写 QQ 号的系统用户及其角色名，绑定与权限判定仍走真实生产链路
     */
    public List<QqSandboxSenderOptionDTO> senderOptions() {
        List<User> users = userRepo.findAllWithQq();
        if (users.isEmpty()) return List.of();
        List<Long> roleIds = users.stream()
                .flatMap(user -> user.getRoles() == null ? java.util.stream.Stream.<RoleID>empty() : user.getRoles().stream())
                .map(RoleID::getValue)
                .distinct()
                .toList();
        Map<Long, String> roleNames = roleRepo.findByIds(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (left, right) -> left));
        return users.stream()
                .map(user -> new QqSandboxSenderOptionDTO(user.getQq().getValue(),
                        user.getNickname() == null ? user.getQq().getValue() : user.getNickname(),
                        String.valueOf(user.getId()),
                        user.getRoles() == null ? List.of() : user.getRoles().stream()
                                .map(role -> roleNames.getOrDefault(role.getValue(), String.valueOf(role.getValue())))
                                .toList()))
                .toList();
    }

    /**
     * 角色模拟可选项：全部启用状态的系统角色，供沙盒会话指定模拟角色集合
     */
    public List<QqSandboxRoleOptionDTO> roleOptions() {
        return roleRepo.findAll().stream()
                .filter(role -> role.getStatus() == online.yudream.base.domain.system.user.enumerate.RoleStatus.ACTIVE)
                .map(role -> new QqSandboxRoleOptionDTO(role.getCode(), role.getName()))
                .toList();
    }

    /**
     * 策略连接的真实群列表与机器人自身 ID；只在用户主动查看选项时发起远程调用，失败时 selfId 置空
     */
    public QqSandboxGroupsDTO groupOptions(String connectionId) {
        MilkyConnection connection = enabledPolicyConnection(connectionId);
        MilkyModels.Context context = new MilkyModels.Context(connection.getBaseUrl(), connection.getToken(), null);
        Object data = milkyApiGateway.invoke(context, "get_group_list", Map.of());
        List<QqSandboxGroupOptionDTO> groups = new java.util.ArrayList<>();
        Object rows = groupRows(data);
        if (rows instanceof Iterable<?> iterable) {
            for (Object row : iterable) {
                if (!(row instanceof Map<?, ?> value)) continue;
                Object id = firstKey(value, "group_id", "group_uin", "id");
                if (id == null) continue;
                Object name = firstKey(value, "group_name", "name", "group_remark");
                groups.add(new QqSandboxGroupOptionDTO(String.valueOf(id),
                        name == null ? String.valueOf(id) : String.valueOf(name)));
            }
        }
        return new QqSandboxGroupsDTO(fetchSelfId(context), List.copyOf(groups));
    }

    private String fetchSelfId(MilkyModels.Context context) {
        try {
            Object data = milkyApiGateway.invoke(context, "get_login_info", Map.of());
            Object rows = groupRows(data);
            if (rows instanceof Map<?, ?> map) {
                Object id = firstKey(map, "user_id", "uin", "user_uin");
                return id == null ? null : String.valueOf(id);
            }
        } catch (RuntimeException ignored) {
            // selfId 只是预填便利，连接不可达时保持手输
        }
        return null;
    }

    private Object groupRows(Object value) {
        if (!(value instanceof Map<?, ?> map)) return value;
        for (String key : List.of("groups", "group_list", "list", "data")) {
            if (map.containsKey(key)) return groupRows(map.get(key));
        }
        return value;
    }

    private Object firstKey(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
        }
        return null;
    }

    public QqSandboxSessionDTO detail(String id) {
        return QqSandboxAssembler.toDTO(session(id));
    }

    public List<QqSandboxCaseDTO> listCases() {
        return caseRepo.findAll().stream().map(QqSandboxAssembler::toCaseDTO).toList();
    }

    /** 保存或覆盖沙盒用例：id 为空新建，非空则保留原创建时间；name/步骤内容做兜底校验 */
    public QqSandboxCaseDTO saveCase(QqSandboxCaseSaveCmd cmd) {
        if (cmd == null || cmd.name() == null || cmd.name().isBlank()) {
            throw new BizException("沙盒用例名称不能为空");
        }
        if (cmd.setup() == null) {
            throw new BizException("沙盒用例缺少会话初始参数");
        }
        if (cmd.steps() == null || cmd.steps().isEmpty()) {
            throw new BizException("沙盒用例至少包含一条消息步骤");
        }
        for (QqSandboxCaseStep step : cmd.steps()) {
            if (step == null) {
                throw new BizException("沙盒用例存在内容为空的消息步骤");
            }
            switch (step.type()) {
                case "message" -> {
                    if (step.content() == null || step.content().isBlank()) {
                        throw new BizException("沙盒用例存在内容为空的消息步骤");
                    }
                }
                case "button" -> {
                    if (step.buttonId() == null || step.buttonId().isBlank()) {
                        throw new BizException("沙盒用例存在缺少按钮 ID 的按钮回调步骤");
                    }
                }
                case "group_request" -> {
                }
                default -> throw new BizException("沙盒用例存在不支持的事件类型: " + step.type());
            }
        }
        Instant now = Instant.now();
        Instant createdAt = now;
        String id = cmd.id() == null || cmd.id().isBlank() ? UUID.randomUUID().toString() : cmd.id().trim();
        if (cmd.id() != null && !cmd.id().isBlank()) {
            QqSandboxCase existing = caseRepo.findById(id)
                    .orElseThrow(() -> new BizException("沙盒用例不存在"));
            createdAt = existing.createdAt();
        }
        QqSandboxCase sandboxCase = new QqSandboxCase(id, cmd.name().trim(), cmd.description(), createdAt, now,
                cmd.setup(), cmd.steps());
        caseRepo.save(sandboxCase);
        return QqSandboxAssembler.toCaseDTO(sandboxCase);
    }

    public void deleteCase(String id) {
        if (caseRepo.findById(id).isEmpty()) {
            throw new BizException("沙盒用例不存在");
        }
        caseRepo.delete(id);
    }

    /**
     * 一键回放用例：按保存的初始参数新建会话，再逐步同步发送保存的消息，
     * 逐步执行保证时间线顺序与会话状态机语义与手工逐条发送完全一致。
     */
    public QqSandboxSessionDTO replayCase(String caseId) {
        QqSandboxCase sandboxCase = caseRepo.findById(caseId)
                .orElseThrow(() -> new BizException("沙盒用例不存在"));
        QqSandboxSessionDTO session = create(QqSandboxAssembler.toCreateCmd(sandboxCase.setup()));
        QqSandboxSession live = session(session.id());
        Map<String, Object> replayPayload = new LinkedHashMap<>();
        replayPayload.put("caseId", sandboxCase.id());
        replayPayload.put("caseName", sandboxCase.name());
        replayPayload.put("steps", sandboxCase.steps().size());
        live.append("session", "case.replay", live.pluginCode(), replayPayload);
        sessions.save(live);
        for (QqSandboxCaseStep step : sandboxCase.steps()) {
            send(session.id(), QqSandboxAssembler.toMessageCmd(step));
        }
        return detail(session.id());
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
        payload.put("type", cmd.type());
        payload.put("senderId", senderId(session, cmd));
        payload.put("nickname", cmd.nickname() == null ? "" : cmd.nickname());
        payload.put("content", cmd.content() == null ? "" : cmd.content());
        payload.put("mentionSelf", cmd.mentionSelf());
        payload.put("mentions", cmd.mentions());
        payload.put("replyMessageId", cmd.replyMessageId() == null ? "" : cmd.replyMessageId());
        payload.put("clientMessageId", cmd.clientMessageId() == null ? "" : cmd.clientMessageId());
        payload.put("buttonId", cmd.buttonId() == null ? "" : cmd.buttonId());
        return payload;
    }

    /** 事件类型决定必填项：消息要内容，按钮回调要按钮 ID，入群请求的验证留言可空 */
    private void validateMessage(QqSandboxMessageCmd cmd) {
        if (cmd == null) {
            throw new BizException("QQ 沙箱消息不能为空");
        }
        switch (cmd.type()) {
            case "message" -> {
                if (cmd.content() == null || cmd.content().isBlank()) {
                    throw new BizException("QQ 沙箱消息不能为空");
                }
            }
            case "button" -> {
                if (cmd.buttonId() == null || cmd.buttonId().isBlank()) {
                    throw new BizException("QQ 沙箱按钮回调必须提供按钮 ID");
                }
            }
            case "group_request" -> {
            }
            default -> throw new BizException("QQ 沙箱不支持的事件类型: " + cmd.type());
        }
    }

    private String syntheticAction(QqSandboxMessageCmd cmd) {
        return switch (cmd.type()) {
            case "button" -> "button.synthetic";
            case "group_request" -> "group_request.synthetic";
            default -> "message.synthetic";
        };
    }

    private String senderId(QqSandboxSession session, QqSandboxMessageCmd cmd) {
        return cmd.senderId() == null || cmd.senderId().isBlank() ? session.userId() : cmd.senderId().trim();
    }

    private void ensurePolicyConnection(String id) {
        if (milkyConnections == null) return;
        enabledPolicyConnection(id);
    }

    private MilkyConnection enabledPolicyConnection(String id) {
        if (milkyConnections == null) throw new BizException("QQ 沙箱策略连接不可用");
        Long connectionId;
        try {
            connectionId = Long.valueOf(id);
        } catch (RuntimeException error) {
            throw new BizException("QQ 沙箱策略连接 ID 无效");
        }
        MilkyConnection connection = milkyConnections.findById(connectionId)
                .orElseThrow(() -> new BizException("QQ 沙箱策略连接不存在"));
        if (!connection.isEnabled()) throw new BizException("QQ 沙箱策略连接未启用");
        return connection;
    }

    private QqSandboxSession session(String id) {
        return sessions.findById(id).orElseThrow(() -> new BizException("QQ 沙箱会话不存在"));
    }
}
