package online.yudream.base.domain.platform.milky.sandbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class QqSandboxSession {
    private final String id;
    private final String pluginCode;
    private final String connectionId;
    private final String policyConnectionId;
    private final String selfId;
    private final String userId;
    private final String nickname;
    private final String channelId;
    private final String scene;
    private final QqSandboxRandomMode randomMode;
    // 身份模拟开关：forceUnbound 强制插件侧视为未绑定；simulateRoles 为 null 走真实角色，空列表表示无角色
    private final boolean forceUnbound;
    private final List<String> simulateRoles;
    private final long timeoutMillis;
    private final Instant createdAt;
    private volatile Instant lastAccessAt;
    private final AtomicLong sequence = new AtomicLong();
    private final CopyOnWriteArrayList<QqSandboxTimelineEvent> timeline = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<QqSandboxTimelineEvent>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Map<String, Map<String, Object>>> documentOverlay = new ConcurrentHashMap<>();
    private final Map<String, Object> semanticOverlay = new ConcurrentHashMap<>();
    private final Map<String, Boolean> activeOperations = new ConcurrentHashMap<>();
    private volatile String status = "READY";

    private QqSandboxSession(String id, String pluginCode, String policyConnectionId, String selfId, String userId,
                             String nickname, String channelId, String scene, QqSandboxRandomMode randomMode,
                             boolean forceUnbound, List<String> simulateRoles, long timeoutMillis, Instant createdAt) {
        this.id = id;
        this.pluginCode = pluginCode;
        this.connectionId = "devtools-sandbox:" + id;
        this.policyConnectionId = requireNumeric(policyConnectionId);
        this.selfId = selfId;
        this.userId = userId;
        this.nickname = nickname;
        this.channelId = channelId;
        this.scene = scene;
        this.randomMode = randomMode;
        this.forceUnbound = forceUnbound;
        this.simulateRoles = simulateRoles;
        this.timeoutMillis = timeoutMillis;
        this.createdAt = createdAt;
        this.lastAccessAt = createdAt;
    }

    /** 插件范围为空串时表示不限定插件，沙盒消息广播给全部已启用插件，与真实 QQ 群一致 */
    public static final String ALL_PLUGINS = "";

    public static QqSandboxSession create(String id, String pluginCode, String policyConnectionId, String selfId,
                                          String userId, String nickname, String channelId, String scene,
                                          QqSandboxRandomMode randomMode, long timeoutMillis, Instant createdAt) {
        return create(id, pluginCode, policyConnectionId, selfId, userId, nickname, channelId, scene, randomMode,
                false, null, timeoutMillis, createdAt);
    }

    public static QqSandboxSession create(String id, String pluginCode, String policyConnectionId, String selfId,
                                          String userId, String nickname, String channelId, String scene,
                                          QqSandboxRandomMode randomMode, boolean forceUnbound, List<String> simulateRoles,
                                          long timeoutMillis, Instant createdAt) {
        return new QqSandboxSession(require(id), normalizePluginCode(pluginCode), policyConnectionId, require(selfId), require(userId),
                normalizeNickname(nickname), require(channelId), normalizeScene(scene),
                randomMode == null ? QqSandboxRandomMode.REAL : randomMode, forceUnbound, normalizeSimulateRoles(simulateRoles),
                Math.min(Math.max(timeoutMillis, 1L), 120_000L), createdAt == null ? Instant.now() : createdAt);
    }

    public QqSandboxTimelineEvent append(String phase, String action, String eventPluginCode, Map<String, Object> payload) {
        touch();
        QqSandboxTimelineEvent event = new QqSandboxTimelineEvent(sequence.incrementAndGet(), Instant.now(), phase,
                action, eventPluginCode, payload);
        timeline.add(event);
        // 订阅者（如 SSE 推送）可能因客户端断开而失败，绝不能反向打断沙盒执行，失败后立即退订
        listeners.forEach(listener -> {
            try {
                listener.accept(event);
            } catch (RuntimeException ignored) {
                listeners.remove(listener);
            }
        });
        return event;
    }

    public AutoCloseable subscribe(Consumer<QqSandboxTimelineEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void running() {
        status = "RUNNING";
    }

    public void completed() {
        status = "COMPLETED";
    }

    public void timedOut() {
        status = "TIMED_OUT";
    }

    public void failed() {
        status = "FAILED";
    }

    public void close() {
        status = "CLOSED";
        listeners.clear();
        documentOverlay.clear();
        semanticOverlay.clear();
        activeOperations.clear();
    }

    public boolean acceptsCaptures() {
        return !"TIMED_OUT".equals(status) && !"FAILED".equals(status) && !"CLOSED".equals(status);
    }

    public String id() {
        return id;
    }

    public String pluginCode() {
        return pluginCode;
    }

    public String connectionId() {
        return connectionId;
    }

    public String policyConnectionId() {
        return policyConnectionId;
    }

    public String selfId() {
        return selfId;
    }

    public String userId() {
        return userId;
    }

    public String nickname() {
        return nickname;
    }

    public String channelId() {
        return channelId;
    }

    public String scene() {
        return scene;
    }

    public QqSandboxRandomMode randomMode() {
        return randomMode;
    }

    public boolean forceUnbound() {
        return forceUnbound;
    }

    /** null 表示走真实角色；空列表表示模拟无角色；否则为模拟角色 code 列表 */
    public List<String> simulateRoles() {
        return simulateRoles;
    }

    public long timeoutMillis() {
        return timeoutMillis;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastAccessAt() {
        return lastAccessAt;
    }

    public void touch() {
        lastAccessAt = Instant.now();
    }

    public void touch(Instant instant) {
        lastAccessAt = instant == null ? Instant.now() : instant;
    }

    public String status() {
        return status;
    }

    public List<QqSandboxTimelineEvent> timeline() {
        return List.copyOf(timeline);
    }

    public Map<String, Map<String, Map<String, Object>>> documentOverlay() {
        return documentOverlay;
    }

    public Map<String, Object> semanticOverlay() {
        return semanticOverlay;
    }

    public void beginOperation(String operationId) {
        if (operationId != null && !operationId.isBlank() && acceptsCaptures()) activeOperations.put(operationId, true);
    }

    public void finishOperation(String operationId) {
        if (operationId != null && !operationId.isBlank()) activeOperations.remove(operationId);
    }

    public boolean hasActiveOperations() {
        return !activeOperations.isEmpty();
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("QQ 沙箱参数不能为空");
        return value.trim();
    }

    private static String requireNumeric(String value) {
        String text = require(value);
        if (!text.matches("[0-9]+")) throw new IllegalArgumentException("QQ 沙箱策略连接 ID 必须为数字字符串");
        return text;
    }

    private static String normalizePluginCode(String pluginCode) {
        if (pluginCode == null || pluginCode.isBlank()) return ALL_PLUGINS;
        return pluginCode.trim();
    }

    private static String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return null;
        return nickname.trim();
    }

    private static String normalizeScene(String scene) {
        String value = require(scene).toLowerCase(java.util.Locale.ROOT);
        if (!"group".equals(value) && !"private".equals(value) && !"friend".equals(value)) {
            throw new IllegalArgumentException("QQ 沙箱会话类型无效");
        }
        return value;
    }

    private static List<String> normalizeSimulateRoles(List<String> simulateRoles) {
        if (simulateRoles == null) return null;
        List<String> normalized = simulateRoles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return List.copyOf(normalized);
    }
}
