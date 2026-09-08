package online.yudream.base.infra.platform.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.user.service.MessagingIdentityAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyApiGateway;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSessionRepo;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageResult;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingConnection;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingGroup;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingRawService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MilkyPluginMessagingService implements PluginMessagingService, PluginMessagingRawService {
    /** 插件可用 [[wiki-image:1]] 这类标记把附件嵌入文本的指定位置；附件 title 与标记内容对应。 */
    private static final Pattern INLINE_ATTACHMENT = Pattern.compile("\\[\\[([A-Za-z][A-Za-z0-9:_-]*)\\]\\]");

    private final MilkyConnectionRepo connectionRepo;
    private final MilkyApiGateway apiGateway;
    private final MessagingIdentityAppService messagingIdentities;
    private final ObjectMapper objectMapper;
    private QqSandboxSessionRepo sandboxSessions;
    private final ExecutorService executor = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), Thread.ofVirtual().name("milky-plugin-messaging-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    void setSandboxSessions(QqSandboxSessionRepo sandboxSessions) {
        this.sandboxSessions = sandboxSessions;
    }

    @Override
    public List<PluginMessagingConnection> connections() {
        return connectionRepo.findEnabled().stream()
                // A picker must only read local connection configuration. Calling get_login_info
                // here turns every plugin page load into a potentially long remote Milky request.
                .map(connection -> new PluginMessagingConnection(
                        String.valueOf(connection.getId()),
                        connection.getName(),
                        "qq",
                        null,
                        connection.protocolCode()))
                .toList();
    }

    @Override
    public List<PluginMessagingGroup> groups(String connectionId) {
        QqSandboxSession sandbox = sandbox(connectionId);
        if (sandbox != null) {
            return inSandbox(sandbox, () -> {
                sandbox.append("output", "messaging.groups", sandbox.pluginCode(), Map.of("connectionId", connectionId));
                return List.of();
            });
        }
        try {
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
        } catch (RuntimeException exception) {
            log.warn("列出消息连接群失败: connectionId={}, errorType={}", connectionId, exception.getClass().getSimpleName());
            return List.of();
        }
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
        QqSandboxSession sandbox = sandbox(request == null ? null : request.connectionId());
        if (sandbox != null) {
            return inSandbox(sandbox, () -> captureMessage(sandbox, "messaging.send", request.connectionId(),
                    request.channelId(), request.content()));
        }
        return async("send", request == null ? null : request.connectionId(), "group", () -> {
            if (request == null || request.content() == null) {
                throw new BizException("插件消息请求不能为空");
            }
            return sendNow(connection(request.connectionId()), request.channelId(),
                    sendScene(request.content()), request.content());
        });
    }

    @Override
    public CompletionStage<PluginMessageResult> sendDirectToBoundUser(String userId, PluginMessageContent content) {
        QqSandboxSession sandbox = QqSandboxExecutionScope.requireActive();
        if (sandbox != null) return inSandbox(sandbox,
                () -> captureMessage(sandbox, "messaging.sendDirect", sandbox.connectionId(), userId, content));
        return async("sendDirect", null, "private", () -> {
            if (content == null) {
                throw new BizException("私聊内容不能为空");
            }
            Long systemUserId;
            try {
                systemUserId = Long.valueOf(userId);
            } catch (RuntimeException exception) {
                throw new BizException("系统用户 ID 无效");
            }
            List<MilkyConnection> connections = connectionRepo.findEnabled();
            if (connections.size() != 1) {
                throw new BizException("私聊需要恰好一个已启用的消息连接");
            }
            MilkyConnection connection = connections.getFirst();
            String peerId = messagingIdentities.privatePeerId(systemUserId, connection)
                    .orElseThrow(() -> new BizException(connection.official()
                            ? "用户尚未绑定官方私聊身份，请先在私聊中完成绑定"
                            : "用户尚未绑定 QQ"));
            return sendNow(connection, peerId, "private", content);
        });
    }

    @Override
    public CompletionStage<PluginMessageResult> sendToChannel(String connectionId, String channelId, PluginMessageContent content) {
        QqSandboxSession sandbox = sandbox(connectionId);
        if (sandbox != null) return inSandbox(sandbox,
                () -> captureMessage(sandbox, "messaging.sendToChannel", connectionId, channelId, content));
        return async("sendToChannel", connectionId, "group", () -> sendNow(connection(connectionId), channelId, "group", content));
    }

    @Override
    public CompletionStage<Map<String, Object>> invoke(String connectionId, String method, Map<String, Object> payload) {
        QqSandboxSession sandbox = sandbox(connectionId);
        if (sandbox != null) {
            return inSandbox(sandbox, () -> captureRaw(sandbox, connectionId, method, payload));
        }
        return async("invoke", connectionId, "api", () -> map(apiGateway.invoke(context(connection(connectionId)), method, payload == null ? Map.of() : payload)));
    }

    private CompletionStage<Map<String, Object>> captureRaw(QqSandboxSession sandbox, String connectionId,
                                                             String method, Map<String, Object> payload) {
        Map<String, Object> body = payload == null ? Map.of() : payload;
        updateActivity(sandbox, method, body);
        Map<String, Object> captured = new LinkedHashMap<>();
        captured.put("connectionId", connectionId);
        captured.put("method", method);
        captured.put("payload", body);
        sandbox.append("output", "messaging.raw.invoke", sandbox.pluginCode(), captured);
        return CompletableFuture.completedFuture(Map.of("sandbox", true, "method", method == null ? "" : method));
    }

    private void updateActivity(QqSandboxSession sandbox, String method, Map<String, Object> payload) {
        if (!"devtools_sandbox_diagnostic".equals(method)) return;
        String milestone = String.valueOf(payload.getOrDefault("milestone", ""));
        String operationId = String.valueOf(payload.getOrDefault("traceId", "agent"));
        if ("agent_pending".equals(milestone) || "agent_start".equals(milestone)) {
            sandbox.beginOperation(operationId);
        } else if ("agent_complete".equals(milestone) || "agent_error".equals(milestone)
                || "trigger_blocked".equals(milestone)) {
            sandbox.finishOperation(operationId);
        }
    }

    private QqSandboxSession sandbox(String connectionId) {
        QqSandboxSession current = QqSandboxExecutionScope.requireActive();
        if (current != null) return current;
        if (connectionId == null || !connectionId.startsWith("devtools-sandbox:")) return null;
        if (sandboxSessions == null) throw new BizException("QQ 沙箱会话注册表不可用");
        return sandboxSessions.findByConnectionId(connectionId)
                .orElseThrow(() -> new BizException("QQ 沙箱会话不存在或已关闭"));
    }

    private <T> T inSandbox(QqSandboxSession sandbox, java.util.function.Supplier<T> action) {
        if (QqSandboxExecutionScope.current() == sandbox) return action.get();
        try (QqSandboxExecutionScope ignored = QqSandboxExecutionScope.open(sandbox)) {
            QqSandboxExecutionScope.requireActive();
            T result = action.get();
            if (result instanceof CompletionStage<?> stage) QqSandboxExecutionScope.wrapCompletion(sandbox, stage);
            return result;
        }
    }

    private CompletionStage<PluginMessageResult> captureMessage(QqSandboxSession sandbox, String action,
                                                                 String connectionId, String channelId,
                                                                 PluginMessageContent content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("connectionId", connectionId == null ? "" : connectionId);
        payload.put("channelId", channelId == null ? "" : channelId);
        payload.put("type", content == null || content.type() == null ? "" : content.type().name());
        payload.put("content", content == null || content.content() == null ? "" : content.content());
        payload.put("referrer", content == null || content.referrer() == null ? Map.of() : content.referrer());
        payload.put("attachments", content == null || content.attachments() == null ? List.of() : content.attachments());
        payload.put("buttons", content == null || content.buttons() == null ? List.of()
                : content.buttons().stream().map(button -> button == null ? "" : button.label()).toList());
        sandbox.append("output", action, sandbox.pluginCode(), payload);
        return CompletableFuture.completedFuture(new PluginMessageResult(
                List.of("sandbox-" + sandbox.timeline().size()), false, false));
    }

    private <T> CompletionStage<T> async(String operation, String connectionId, String channelType, java.util.function.Supplier<T> action) {
        try {
            return CompletableFuture.supplyAsync(action, executor).whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Milky plugin async operation failed: operation={}, connectionId={}, channelType={}, errorType={}",
                            operation, connectionId, channelType, exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException exception) {
            log.error("Milky plugin async operation rejected: operation={}, connectionId={}, channelType={}, errorType={}",
                    operation, connectionId, channelType, exception.getClass().getSimpleName());
            return CompletableFuture.failedFuture(exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private PluginMessageResult sendNow(MilkyConnection connection, String peer, String scene, PluginMessageContent content) {
        if (peer == null || peer.isBlank()) {
            throw new BizException("消息目标不能为空");
        }
        String resolved = blank(scene) ? "group" : scene.trim().toLowerCase();
        String api = switch (resolved) {
            case "channel" -> "send_channel_message";
            case "dm" -> "send_guild_dm";
            case "friend", "private" -> "send_private_message";
            default -> "send_group_message";
        };
        String idKey = switch (resolved) {
            case "channel" -> "channel_id";
            case "dm" -> "guild_id";
            case "friend", "private" -> "user_id";
            default -> "group_id";
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(idKey, peer);
        if ("dm".equals(resolved)) {
            body.put("channel_id", peer);
        }
        boolean degraded = false;
        if (connection.official()) {
            degraded = buildOfficialBody(body, content);
        } else {
            Map<String, Object> segment = switch (content.type()) {
                case IMAGE -> Map.of("type", "image", "data", Map.of("uri", content.content()));
                case AUDIO -> Map.of("type", "record", "data", Map.of("uri", content.content()));
                case VIDEO -> Map.of("type", "video", "data", Map.of("uri", content.content()));
                case FILE -> Map.of("type", "file", "data", Map.of("uri", content.content()));
                case COMPOSITE -> Map.of("type", "forward", "data", compositeData(content.content()));
                default -> Map.of("type", "text", "data", Map.of("text", content.content()));
            };
            body.put("message", messageSegments(content, segment));
            degraded = !content.buttons().isEmpty();
            if (degraded) {
                log.debug("消息连接的协议不支持交互按钮，已降级忽略: connectionId={}", connection.getId());
            }
        }
        copyReplyIds(content.referrer(), body);
        Map<String, Object> result = map(apiGateway.invoke(context(connection), api, body));
        Object messageId = result.get("message_seq");
        if (messageId == null) {
            messageId = result.get("message_id");
        }
        if (messageId == null) {
            messageId = result.get("id");
        }
        return new PluginMessageResult(List.of(String.valueOf(messageId == null ? "" : messageId)), false, degraded);
    }

    /**
     * 官方 QQ 机器人特异化出站：无富媒体时一律以 msg_type=2 markdown 被动回复，
     * 文本中的 [[token]] 附件标记内嵌为 markdown 图片；带可上传媒体时降级为 msg_type=7
     * 并把正文作为 caption。按钮映射为 keyboard；msg_id/event_id 由 copyReplyIds 与
     * 适配器的 lastInbound 兜底共同保证被动回复。
     *
     * @return 是否有内容被降级丢弃
     */
    private boolean buildOfficialBody(Map<String, Object> body, PluginMessageContent content) {
        boolean degraded = false;
        Map<String, Object> media = officialMedia(content);
        List<PluginMessageContent.Attachment> inlineImages = List.of();
        if (media == null && !content.attachments().isEmpty()
                && (content.type() == PluginMessageContent.Type.TEXT || content.type() == PluginMessageContent.Type.MARKDOWN)) {
            List<PluginMessageContent.Attachment> remoteImages = new ArrayList<>();
            List<PluginMessageContent.Attachment> others = new ArrayList<>();
            for (PluginMessageContent.Attachment attachment : content.attachments()) {
                if (remoteImage(attachment)) {
                    remoteImages.add(attachment);
                } else {
                    others.add(attachment);
                }
            }
            if (others.isEmpty()) {
                inlineImages = remoteImages;
            } else {
                PluginMessageContent.Attachment first = others.getFirst();
                media = Map.of("file_type", officialFileType(first.contentType()), "url", first.url());
                degraded = others.size() > 1 || !remoteImages.isEmpty();
            }
        }
        String text = content.content() == null ? "" : content.content();
        if (media != null) {
            body.put("msg_type", 7);
            body.put("media", media);
            // 官方 msg_type=7 的 content 是图片说明。可见空格、换行都会和图片同气泡，把图压小。
            // 富媒体消息一律独占气泡，说明文字由插件另发文本消息。
            body.put("content", OFFICIAL_MEDIA_PLACEHOLDER);
        } else {
            body.put("msg_type", 2);
            body.put("content", text.isBlank() ? " " : text);
            body.put("markdown", Map.of("content", markdownWithInlineImages(text, inlineImages)));
        }
        Map<String, Object> keyboard = officialKeyboard(content.buttons());
        if (keyboard != null) {
            body.put("keyboard", keyboard);
        }
        return degraded;
    }

    private static Map<String, Object> officialMedia(PluginMessageContent content) {
        return switch (content.type()) {
            case IMAGE -> Map.of("file_type", 1, "url", content.content());
            case VIDEO -> Map.of("file_type", 2, "url", content.content());
            case AUDIO -> Map.of("file_type", 3, "url", content.content());
            case FILE -> Map.of("file_type", 4, "url", content.content());
            default -> null;
        };
    }

    /** 官方富媒体必填但不可见的 caption，避免普通空格/换行把图片压成缩略图。 */
    static final String OFFICIAL_MEDIA_PLACEHOLDER = "\u200B";

    private static int officialFileType(String contentType) {
        String value = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
        if (value.startsWith("video/")) {
            return 2;
        }
        if (value.startsWith("audio/")) {
            return 3;
        }
        return value.startsWith("image/") ? 1 : 4;
    }

    private static boolean remoteImage(PluginMessageContent.Attachment attachment) {
        if (attachment == null || attachment.url() == null) {
            return false;
        }
        String contentType = attachment.contentType() == null ? "" : attachment.contentType().toLowerCase(java.util.Locale.ROOT);
        String url = attachment.url();
        return contentType.startsWith("image/") && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /** 官方 markdown 支持 ![标题](url) 内嵌公网图片，复用 [[token]] 标记位置；未引用图片追加到末尾。 */
    private static String markdownWithInlineImages(String text, List<PluginMessageContent.Attachment> inlineImages) {
        if (inlineImages.isEmpty()) {
            return text;
        }
        Map<String, PluginMessageContent.Attachment> byToken = new LinkedHashMap<>();
        for (PluginMessageContent.Attachment attachment : inlineImages) {
            if (attachment.title() != null && !attachment.title().isBlank()) {
                byToken.putIfAbsent(attachment.title().trim(), attachment);
            }
        }
        StringBuilder markdown = new StringBuilder();
        LinkedHashSet<PluginMessageContent.Attachment> used = new LinkedHashSet<>();
        Matcher matcher = INLINE_ATTACHMENT.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            markdown.append(text, cursor, matcher.start());
            PluginMessageContent.Attachment attachment = byToken.get(matcher.group(1));
            if (attachment == null) {
                markdown.append(matcher.group());
            } else {
                markdown.append(markdownImage(attachment));
                used.add(attachment);
            }
            cursor = matcher.end();
        }
        markdown.append(text.substring(cursor));
        for (PluginMessageContent.Attachment attachment : inlineImages) {
            if (!used.contains(attachment)) {
                markdown.append('\n').append(markdownImage(attachment));
            }
        }
        return markdown.toString();
    }

    private static String markdownImage(PluginMessageContent.Attachment attachment) {
        String title = attachment.title() == null || attachment.title().isBlank() ? "图片" : attachment.title().trim();
        return "![" + title + "](" + attachment.url() + ")";
    }

    /** SPI 按钮 → 官方 keyboard：指令按钮 action.type=2，回调按钮 action.type=1；每排最多 2 个、最多 5 行。 */
    private static Map<String, Object> officialKeyboard(List<PluginMessageContent.Button> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> row = new ArrayList<>();
        int index = 0;
        for (PluginMessageContent.Button item : buttons) {
            if (item == null || blank(item.label()) || blank(item.data())) {
                continue;
            }
            Map<String, Object> render = new LinkedHashMap<>();
            render.put("label", item.label());
            render.put("visited_label", item.label());
            render.put("style", 1);
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("type", item.enter() ? 2 : 1);
            action.put("permission", Map.of("type", 2));
            action.put("click_limit", 0);
            action.put("data", item.data());
            action.put("enter", item.enter());
            action.put("reply", false);
            Map<String, Object> button = new LinkedHashMap<>();
            button.put("id", blank(item.id()) ? "btn-" + index : item.id());
            button.put("render_data", render);
            button.put("action", action);
            row.add(button);
            index++;
            if (row.size() == 2) {
                rows.add(Map.of("buttons", List.copyOf(row)));
                row.clear();
            }
            if (rows.size() == 5) {
                break;
            }
        }
        if (!row.isEmpty() && rows.size() < 5) {
            rows.add(Map.of("buttons", List.copyOf(row)));
        }
        return rows.isEmpty() ? null : Map.of("content", Map.of("rows", rows));
    }

    /** 普通文本保持“正文 + 附件追加”的兼容行为；带标记文本则按标记位置内嵌附件。 */
    private List<Map<String, Object>> messageSegments(PluginMessageContent content, Map<String, Object> baseSegment) {
        if (content.type() != PluginMessageContent.Type.TEXT
                || content.content() == null
                || content.attachments().isEmpty()
                || !INLINE_ATTACHMENT.matcher(content.content()).find()) {
            List<Map<String, Object>> message = new java.util.ArrayList<>();
            message.add(baseSegment);
            for (PluginMessageContent.Attachment attachment : content.attachments()) {
                Map<String, Object> attachmentSegment = attachmentSegment(attachment);
                if (attachmentSegment != null) {
                    message.add(attachmentSegment);
                }
            }
            return message;
        }

        Map<String, PluginMessageContent.Attachment> byToken = new LinkedHashMap<>();
        for (PluginMessageContent.Attachment attachment : content.attachments()) {
            if (attachment.title() != null && !attachment.title().isBlank()) {
                byToken.putIfAbsent(attachment.title().trim(), attachment);
            }
        }
        List<Map<String, Object>> message = new java.util.ArrayList<>();
        LinkedHashSet<PluginMessageContent.Attachment> used = new LinkedHashSet<>();
        Matcher matcher = INLINE_ATTACHMENT.matcher(content.content());
        int cursor = 0;
        while (matcher.find()) {
            addTextSegment(message, content.content().substring(cursor, matcher.start()));
            PluginMessageContent.Attachment attachment = byToken.get(matcher.group(1));
            Map<String, Object> attachmentSegment = attachmentSegment(attachment);
            if (attachmentSegment == null) {
                addTextSegment(message, matcher.group());
            }
            else {
                message.add(attachmentSegment);
                used.add(attachment);
            }
            cursor = matcher.end();
        }
        addTextSegment(message, content.content().substring(cursor));
        // 未被显式引用的附件仍追加到末尾，兼容既有“图文混排附件”语义。
        for (PluginMessageContent.Attachment attachment : content.attachments()) {
            if (used.contains(attachment)) {
                continue;
            }
            Map<String, Object> attachmentSegment = attachmentSegment(attachment);
            if (attachmentSegment != null) {
                message.add(attachmentSegment);
            }
        }
        return message;
    }

    private void addTextSegment(List<Map<String, Object>> message, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        message.add(Map.of("type", "text", "data", Map.of("text", text)));
    }

    /** 附件转消息分段：仅支持可内联的媒体类型，未知类型跳过 */
    private Map<String, Object> attachmentSegment(PluginMessageContent.Attachment attachment) {
        if (attachment == null || attachment.url() == null || attachment.url().isBlank()) {
            return null;
        }
        String contentType = attachment.contentType() == null ? "" : attachment.contentType().toLowerCase(java.util.Locale.ROOT);
        String type = contentType.startsWith("image/") ? "image"
                : contentType.startsWith("audio/") ? "record"
                : contentType.startsWith("video/") ? "video"
                : null;
        if (type == null) {
            return null;
        }
        return Map.of("type", type, "data", Map.of("uri", attachment.url()));
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

    private static String sendScene(PluginMessageContent content) {
        if (content == null || content.referrer() == null) {
            return "group";
        }
        Object scene = content.referrer().get("message_scene");
        if (scene == null) {
            scene = content.referrer().get("messageScene");
        }
        if (scene == null) {
            return "group";
        }
        String value = String.valueOf(scene).trim().toLowerCase();
        if ("private".equals(value)) {
            return "friend";
        }
        if ("channel".equals(value) || "dm".equals(value) || "friend".equals(value) || "group".equals(value)) {
            return value;
        }
        return "group";
    }

    private static void copyReplyIds(Map<String, Object> referrer, Map<String, Object> body) {
        if (referrer == null || referrer.isEmpty()) {
            return;
        }
        putReplyId(body, "msg_id", referrer.get("msg_id"), referrer.get("message_id"), referrer.get("messageId"));
        putReplyId(body, "message_id", referrer.get("message_id"), referrer.get("msg_id"), referrer.get("messageId"));
        putReplyId(body, "event_id", referrer.get("event_id"), referrer.get("eventId"));
    }

    private static void putReplyId(Map<String, Object> body, String key, Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                body.putIfAbsent(key, String.valueOf(value));
                return;
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
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
        return connection.toApiContext();
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
