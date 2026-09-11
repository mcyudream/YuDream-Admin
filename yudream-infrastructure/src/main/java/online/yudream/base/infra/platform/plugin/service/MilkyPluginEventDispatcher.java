package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.milky.service.MilkyConnectionAppService;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.application.system.user.service.MessagingIdentityAppService;
import online.yudream.base.application.system.user.service.MessagingIdentityBindScope;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.event.MilkyEventPublished;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;
import online.yudream.base.infra.platform.milky.official.OfficialQqBotEventNormalizer;
import online.yudream.base.infra.platform.milky.official.OfficialQqBotSessionStore;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.plugin.spi.system.command.PluginCommandService;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class MilkyPluginEventDispatcher {
    private final JarPluginRuntimeGateway runtime;
    private final MessagingIdentityAppService identities;
    private final RoleRepo roles;
    private final SettingRepo settings;
    private final PluginCommandService commands;
    private final MilkyPluginMessagingService messaging;
    private final MilkyConnectionRepo connections;
    private final MilkyConnectionAppService connectionApps;
    private final OfficialQqBotSessionStore officialSessions;
    private final PluginRenderService renderer;
    private final FileAppService files;
    private final TemplateEngine templateEngine;

    static final long MENU_IMAGE_DEADLINE_SECONDS = 15;
    /** 无插件归属指令在菜单中的分组名，也是 /菜单 过滤系统指令的伪 code。 */
    static final String SYSTEM_MENU_GROUP = "系统";

    @EventListener
    public void dispatch(MilkyEventPublished published) {
        dispatch(published == null || published.connectionId() == null ? null : String.valueOf(published.connectionId()),
                published == null ? null : published.event(), Map.of());
    }

    void dispatchSandbox(String connectionId,
                         online.yudream.base.domain.platform.milky.model.MilkyModels.Event event,
                         Map<String, Object> sandboxReferrer) {
        dispatch(connectionId, event, sandboxReferrer == null ? Map.of() : sandboxReferrer);
    }

    private void dispatch(String connectionId,
                          online.yudream.base.domain.platform.milky.model.MilkyModels.Event sourceEvent,
                          Map<String, Object> additionalReferrer) {
        online.yudream.base.domain.platform.milky.model.MilkyModels.Event event = null;
        String messageSeq = null;
        try {
            event = sourceEvent;
            Map<String, Object> data = eventData(event);
            messageSeq = text(data.get("message_seq"));
            if (event == null) {
                return;
            }
            if ("group_request".equals(event.eventType()) || "group_join_request".equals(event.eventType())) {
                dispatchGroupRequest(connectionId, event, data, additionalReferrer);
                return;
            }
            if ("button_click".equals(event.eventType())) {
                dispatchButtonClick(connectionId, event, data, additionalReferrer);
                return;
            }
            if (!isMessageEvent(event.eventType())) {
                return;
            }
            String userId = messageUserId(data);
            String channelId = messageChannelId(data);
            String content = messageContent(data);
            Map<String, Object> referrer = new java.util.LinkedHashMap<>(additionalReferrer);
            java.util.List<String> mentions = new java.util.ArrayList<>(mentionsFromSegments(data.get("segments")));
            if (officialDirectedAtBot(data)) {
                referrer.put("mentionSelf", true);
                // 官方 OpenAPI 的 @机器人/私聊事件没有机器人 mention 段（normalizer 已剥离），补入 selfId 对齐
                // Milky/onebot "@bot 携带 mention 段" 语义，插件只按 mentions 判定也能命中官方定向消息
                String selfId = text(event.selfId());
                if (selfId != null && !mentions.contains(selfId)) {
                    mentions.add(selfId);
                }
            }
            referrer.put("mentions", mentions);
            copyOfficialReplyIds(data, referrer);
            String replyMessageId = replyMessageId(data.get("segments"));
            if (replyMessageId != null) referrer.put("replyMessageId", replyMessageId);
            Parsed command = parseCommand(content);
            PluginEvent pluginEvent = new PluginEvent(String.valueOf(event.time()), event.eventType(), "milky", userId, channelId,
                    content, null, command == null ? null : command.name(), referrer, event.eventType(), data, connectionId,
                    event.selfId(), messageSeq);
            MilkyConnection connection = connectionOf(connectionId);
            String scene = firstText(data, "message_scene");
            String groupOpenid = officialGroupOpenid(connection, scene, channelId);
            User user = identities.findUserByEvent(connection, scene, userId, groupOpenid).orElse(null);
            try (MessagingIdentityBindScope ignored = MessagingIdentityBindScope.open(
                    new MessagingIdentityBindScope.Context(
                            connection == null ? null : connection.protocolOrDefault(),
                            parseConnectionId(connectionId),
                            connection == null ? null : connection.getAppId(),
                            scene, groupOpenid, userId))) {
                if (command == null) {
                    runtime.publishMessagingEvent(pluginEvent);
                    return;
                }
                if (isMenuAlias(command.name())) {
                    menuImage(pluginEvent, user, command.arguments());
                    return;
                }
                if (BIND_MENTION_COMMAND.equals(command.name())) {
                    bindBotMention(pluginEvent, command, connection);
                    return;
                }
                if (user == null && !"绑定".equals(command.name()) && (requiresBound() || commandRequiresBound(command.name()))) {
                    replyBindHint(pluginEvent);
                    return;
                }
                runtime.publishCommand(pluginEvent, command.name(), command.arguments(), user == null ? null : user.getId(),
                        permission -> allowed(user, permission));
            }
        } catch (Exception error) {
            java.util.Map<String, Object> context = new java.util.LinkedHashMap<>();
            context.put("connectionId", connectionId);
            context.put("eventType", event == null ? null : event.eventType());
            context.put("messageSeq", messageSeq);
            QqSandboxDiagnostics.appendError("dispatch.error", null, error, context);
            log.error("Milky plugin event dispatch failed: connectionId={}, eventType={}, selfId={}, messageSeq={}, errorType={}",
                    connectionId, event == null ? null : event.eventType(), event == null ? null : event.selfId(),
                    messageSeq, error.getClass().getSimpleName());
        }
    }

    private void dispatchGroupRequest(String connectionId,
                                      online.yudream.base.domain.platform.milky.model.MilkyModels.Event event,
                                      Map<String, Object> data, Map<String, Object> additionalReferrer) {
        GroupRequest request = groupRequest(data);
        if (request == null) {
            log.warn("Ignoring incomplete Milky group request event: connectionId={}, eventType={}, selfId={}, messageSeq={}",
                    connectionId, event.eventType(), event.selfId(), text(data.get("message_seq")));
            return;
        }
        Map<String, Object> referrer = new java.util.LinkedHashMap<>(additionalReferrer);
        referrer.put("requestId", request.requestId());
        String comment = request.comment();
        if (comment != null) {
            referrer.put("comment", comment);
        }
        PluginEvent pluginEvent = new PluginEvent(String.valueOf(event.time()), "group_request", "milky", request.userId(), request.groupId(),
                comment, null, null, referrer, event.eventType(), data, connectionId,
                event.selfId(), request.requestId());
        runtime.publishMessagingEvent(pluginEvent);
    }

    static Map<String, Object> eventData(online.yudream.base.domain.platform.milky.model.MilkyModels.Event event) {
        return event == null || event.data() == null ? Map.of() : event.data();
    }

    /**
     * 按钮回调事件：buttonId 路由到插件 onButton 交互；生产链路暂不产生该事件类型，仅 QQ 沙盒合成
     */
    private void dispatchButtonClick(String connectionId,
                                     online.yudream.base.domain.platform.milky.model.MilkyModels.Event event,
                                     Map<String, Object> data, Map<String, Object> additionalReferrer) {
        String buttonId = firstText(data, "button_id", "buttonId");
        if (buttonId == null) {
            log.warn("Ignoring Milky button click without button id: connectionId={}, selfId={}, messageSeq={}, nativeType={}",
                    connectionId, event.selfId(), text(data.get("message_seq")), data.get("native_type"));
            return;
        }
        String userId = messageUserId(data);
        String channelId = messageChannelId(data);
        Map<String, Object> referrer = new java.util.LinkedHashMap<>(additionalReferrer);
        copyOfficialReplyIds(data, referrer);
        PluginEvent pluginEvent = new PluginEvent(String.valueOf(event.time()), "button_click", "milky", userId, channelId,
                null, buttonId, null, referrer, event.eventType(), data, connectionId,
                event.selfId(), text(data.get("message_seq")));
        runtime.publishMessagingEvent(pluginEvent);
    }

    static GroupRequest groupRequest(Map<String, Object> data) {
        String groupId = firstText(data, "group_id", "group_uin", "peer_id");
        String userId = firstText(data, "user_id", "applicant_id", "initiator_id", "sender_id", "member_openid");
        String requestId = firstText(data, "request_id", "join_request_id", "notification_seq", "flag", "id");
        String comment = firstText(data, "comment", "message", "verify_message");
        if (comment == null || comment.isBlank()) {
            comment = verifyInfoComment(data.get("verify_info"));
            if (comment == null && data.get("native") instanceof Map<?, ?> nativeData) {
                comment = verifyInfoComment(nativeData.get("verify_info"));
            }
        }
        return groupId == null || userId == null || requestId == null ? null
                : new GroupRequest(groupId, userId, requestId, comment);
    }

    private static String verifyInfoComment(Object raw) {
        if (!(raw instanceof Map<?, ?> info)) {
            return null;
        }
        Object message = info.get("verify_message");
        if (message != null && !String.valueOf(message).isBlank()) {
            return String.valueOf(message);
        }
        Object list = info.get("review_qa_list");
        if (!(list instanceof List<?> qaList) || qaList.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (Object item : qaList) {
            if (!(item instanceof Map<?, ?> qa)) {
                continue;
            }
            String question = qa.get("question") == null ? "" : String.valueOf(qa.get("question")).trim();
            String answer = qa.get("answer") == null ? "" : String.valueOf(qa.get("answer")).trim();
            if (question.isEmpty() && answer.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            if (!question.isEmpty()) {
                builder.append(question).append('：');
            }
            builder.append(answer);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private CompletionStage<?> menu(PluginEvent event, User user, String pluginFilter) {
        var accessible = commands.listAccessible(user == null ? null : user.getId());
        String filter = effectiveMenuFilter(accessible, pluginFilter);
        var commandList = filterCommands(accessible, filter);
        if (officialConnection(event.connectionId())) {
            return messaging.send(new PluginMessageRequest(event.connectionId(), "qq", event.selfId(), event.channelId(),
                    new PluginMessageContent(PluginMessageContent.Type.MARKDOWN,
                            commandMenuMarkdown(nickname(user), commandList, filter != null), null, event.referrer(),
                            menuButtons(commandList, filter))));
        }
        StringBuilder content = new StringBuilder(filter == null ? "可用指令：" : "可用指令（" + filter + "）：");
        commandList.forEach(command -> {
            content.append("\n/").append(command.command());
            if (filter != null) {
                content.append(" - ").append(command.description());
            }
        });
        return sendMenuText(event, content.toString());
    }

    /** 官方连接具备原生图片 + markdown keyboard：菜单以卡片图片为主，按钮仅作交互辅助。 */
    private boolean officialConnection(String connectionId) {
        try {
            return connections.findById(Long.valueOf(connectionId))
                    .map(online.yudream.base.domain.platform.milky.aggregate.MilkyConnection::official)
                    .orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 官方菜单辅助按钮。一级菜单每个插件一个入口（点击发出 /菜单 {插件} 进入二级），
     * 二级菜单展示该插件子指令并附"返回菜单"。officialKeyboard 控制每排最多 2 个、最多 5 排。
     */
    private List<PluginMessageContent.Button> menuButtons(List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list, String pluginFilter) {
        List<PluginMessageContent.Button> buttons = new java.util.ArrayList<>();
        if (pluginFilter == null) {
            Map<String, String> plugins = new java.util.LinkedHashMap<>();
            list.forEach(command -> {
                String code = command.pluginCode() == null || command.pluginCode().isBlank() ? SYSTEM_MENU_GROUP : command.pluginCode();
                plugins.putIfAbsent(code, SYSTEM_MENU_GROUP.equals(code) ? SYSTEM_MENU_GROUP : runtime.displayName(code));
            });
            int index = 0;
            for (Map.Entry<String, String> plugin : plugins.entrySet()) {
                if (index >= 10) {
                    break;
                }
                buttons.add(PluginMessageContent.Button.command("menu-plugin-" + index,
                        buttonLabel(plugin.getValue()), "/菜单 " + plugin.getKey()));
                index++;
            }
            return buttons;
        }
        int index = 0;
        for (online.yudream.base.plugin.spi.system.command.PluginCommandInfo command : list) {
            if (index >= 9) {
                break;
            }
            buttons.add(PluginMessageContent.Button.command("menu-cmd-" + index,
                    buttonLabel(command.name()), "/" + command.command()));
            index++;
        }
        buttons.add(PluginMessageContent.Button.command("menu-back", "🔙 返回菜单", "/菜单"));
        return buttons;
    }

    private void menuImage(PluginEvent event, User user, List<String> arguments) {
        String pluginFilter = menuPluginFilter(arguments);
        if (officialConnection(event.connectionId())) {
            officialMenuImage(event, user, pluginFilter);
            return;
        }
        AtomicBoolean fallbackStarted = new AtomicBoolean();
        try {
            var accessible = commands.listAccessible(user == null ? null : user.getId());
            String filter = effectiveMenuFilter(accessible, pluginFilter);
            var commandList = filterCommands(accessible, filter);
            CompletionStage<?> imageSend = renderer.html(commandMenuHtmlTemplate(nickname(user), commandList, filter != null))
                    .thenCompose(QqSandboxExecutionScope.wrap(image -> {
                        return messaging.send(new PluginMessageRequest(event.connectionId(), "qq", event.selfId(), event.channelId(),
                                new PluginMessageContent(PluginMessageContent.Type.IMAGE,
                                        resolveMenuImageUri(event, image), null, Map.of())));
                    }));
            CompletionStage<?> deadline = withMenuDeadline(imageSend);
            QqSandboxExecutionScope.track(deadline);
            fallbackOnMenuImageFailure(deadline, fallbackStarted, () -> menu(event, user, filter), event);
        } catch (Exception error) {
            fallbackOnMenuImageFailure(failedStage(error), fallbackStarted, () -> menu(event, user, pluginFilter), event);
        }
    }

    /**
     * 官方连接菜单：渲染卡片图片先行发出（图片为主），随后一条短 markdown 携带辅助按钮；
     * 渲染或图片发送失败时降级为 markdown 文字菜单 + 同一组按钮。
     */
    private void officialMenuImage(PluginEvent event, User user, String pluginFilter) {
        var accessible = commands.listAccessible(user == null ? null : user.getId());
        String filter = effectiveMenuFilter(accessible, pluginFilter);
        var commandList = filterCommands(accessible, filter);
        AtomicBoolean fallbackStarted = new AtomicBoolean();
        try {
            CompletionStage<?> imageSend = renderer.html(commandMenuHtmlTemplate(nickname(user), commandList, filter != null))
                    .thenCompose(QqSandboxExecutionScope.wrap(image -> {
                        return messaging.send(new PluginMessageRequest(event.connectionId(), "qq", event.selfId(), event.channelId(),
                                new PluginMessageContent(PluginMessageContent.Type.IMAGE,
                                        resolveMenuImageUri(event, image), null, event.referrer())));
                    }));
            CompletionStage<?> deadline = withMenuDeadline(imageSend);
            QqSandboxExecutionScope.track(deadline);
            fallbackOnMenuImageFailure(deadline, fallbackStarted, () -> menu(event, user, filter), event);
            deadline.thenCompose(QqSandboxExecutionScope.wrap(ignored -> {
                        return sendOfficialMenuButtons(event, commandList, filter);
                    }))
                    .whenComplete((ignored, error) -> {
                        if (error != null && !fallbackStarted.get()) {
                            log.error("官方菜单按钮消息发送失败: connectionId={}, channelId={}",
                                    event.connectionId(), event.channelId(), error);
                        }
                    });
        } catch (Exception error) {
            fallbackOnMenuImageFailure(failedStage(error), fallbackStarted, () -> menu(event, user, filter), event);
        }
    }

    /** 官方菜单辅助按钮消息：一条短 markdown 携带 keyboard，菜单卡片图片已先行发出。 */
    private CompletionStage<?> sendOfficialMenuButtons(PluginEvent event,
                                                       List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> commandList,
                                                       String pluginFilter) {
        List<PluginMessageContent.Button> buttons = menuButtons(commandList, pluginFilter);
        if (buttons.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        String text = pluginFilter == null
                ? "**🤖 指令菜单**\n\n> 菜单卡片见上方图片，点击按钮查看对应插件的指令"
                : "**" + markdown(SYSTEM_MENU_GROUP.equals(pluginFilter) ? SYSTEM_MENU_GROUP : runtime.displayName(pluginFilter))
                        + "** 指令\n\n> 点击按钮直接发送对应指令";
        return messaging.send(new PluginMessageRequest(event.connectionId(), "qq", event.selfId(), event.channelId(),
                new PluginMessageContent(PluginMessageContent.Type.MARKDOWN, text, null, event.referrer(), buttons)));
    }

    /** 菜单指令参数：/菜单 {插件名或插件 code}。 */
    private String menuPluginFilter(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        String value = String.join(" ", arguments).trim();
        return value.isEmpty() ? null : value;
    }

    /** 过滤参数无匹配插件时回退为全量菜单，避免空白卡片。 */
    private String effectiveMenuFilter(List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> accessible, String pluginFilter) {
        if (pluginFilter == null) {
            return null;
        }
        return filterCommands(accessible, pluginFilter).isEmpty() ? null : pluginFilter;
    }

    private List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> filterCommands(
            List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list, String pluginFilter) {
        if (pluginFilter == null) {
            return list;
        }
        return list.stream().filter(command -> {
            String code = command.pluginCode();
            if (code == null || code.isBlank()) {
                return SYSTEM_MENU_GROUP.equals(pluginFilter);
            }
            return code.equalsIgnoreCase(pluginFilter) || runtime.displayName(code).equalsIgnoreCase(pluginFilter);
        }).toList();
    }

    private String nickname(User user) {
        return user == null ? "访客"
                : (user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname());
    }

    /** 官方按钮显示文案保护：过长截断，避免一排两个时挤压换行。 */
    private String buttonLabel(String value) {
        String label = value == null || value.isBlank() ? "指令" : value.trim();
        return label.length() > 12 ? label.substring(0, 11) + "…" : label;
    }

    static <T> CompletionStage<T> withMenuDeadline(CompletionStage<T> stage) {
        return withMenuDeadline(stage, MENU_IMAGE_DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    static <T> CompletionStage<T> withMenuDeadline(CompletionStage<T> stage, long timeout, TimeUnit unit) {
        return stage.toCompletableFuture().orTimeout(timeout, unit);
    }

    static <T> CompletionStage<T> failedStage(Throwable error) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(error);
        return result;
    }

    private void fallbackOnMenuImageFailure(CompletionStage<?> imageSend, AtomicBoolean fallbackStarted,
                                            java.util.function.Supplier<CompletionStage<?>> fallback, PluginEvent event) {
        imageSend.whenComplete(QqSandboxExecutionScope.wrap((ignored, error) -> {
            if (error == null || !fallbackStarted.compareAndSet(false, true)) {
                return;
            }
            log.warn("Milky 菜单图片渲染或发送失败，降级为文本菜单: connectionId={}, channelId={}",
                    event.connectionId(), event.channelId(), error);
            try {
                fallback.get().whenComplete(QqSandboxExecutionScope.wrap((fallbackIgnored, fallbackError) -> {
                    if (fallbackError != null) {
                        log.error("Milky 菜单文本降级发送失败: connectionId={}, channelId={}",
                                event.connectionId(), event.channelId(), fallbackError);
                    }
                }));
            } catch (Exception fallbackError) {
                log.error("Milky 菜单文本降级构建失败: connectionId={}, channelId={}",
                        event.connectionId(), event.channelId(), fallbackError);
            }
        }));
    }

    private CompletionStage<?> sendMenuText(PluginEvent event, String content) {
        return messaging.send(new PluginMessageRequest(event.connectionId(), "qq", event.selfId(), event.channelId(),
                new PluginMessageContent(PluginMessageContent.Type.TEXT, content, null, event.referrer())));
    }

    private String resolveMenuImageUri(PluginEvent event, online.yudream.base.plugin.spi.system.render.PluginRenderedImage image) {
        QqSandboxSession sandbox = QqSandboxExecutionScope.current();
        String mode;
        String publicBaseUrl;
        if (sandbox != null) {
            mode = "base64";
            publicBaseUrl = null;
        } else {
            var connection = connections.findById(Long.valueOf(event.connectionId())).orElse(null);
            mode = connection == null ? "base64" : connection.getCommandMenuImageMode();
            publicBaseUrl = connection == null ? null : connection.getCommandMenuPublicBaseUrl();
        }
        return "url".equalsIgnoreCase(mode)
                ? uploadMenuImage(image, publicBaseUrl)
                : "base64://" + Base64.getEncoder().encodeToString(image.content());
    }

    private String uploadMenuImage(online.yudream.base.plugin.spi.system.render.PluginRenderedImage image, String publicBaseUrl) {
        FileObjectDTO file = files.upload(new ByteArrayInputStream(image.content()), "command-menu.png", image.contentType(), image.content().length,
                "command-menu", null, true);
        String relative = file.getUrl();
        String base = publicBaseUrl == null ? "" : publicBaseUrl;
        return base.isBlank() ? relative : base.replaceAll("/$", "") + relative;
    }

    private String commandMenuHtml(String nickname, String avatar, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list) {
        StringBuilder rows = new StringBuilder();
        list.forEach(command -> rows.append("<div class='row'><b>/").append(escape(command.command())).append("</b><span>")
                .append(escape(command.name())).append("</span><small>").append(escape(command.description())).append("</small></div>"));
        if (rows.isEmpty()) rows.append("<div class='empty'>暂无可用指令</div>");
        return "<!doctype html><html><meta charset='utf-8'><style>body{margin:0;background:#f4f7fb;font-family:Arial,'Microsoft YaHei',sans-serif;color:#1f2937}.card{width:680px;box-sizing:border-box;padding:28px 30px;background:#fff;border-radius:22px;border:1px solid #e5eaf2;box-shadow:0 12px 36px #1f3b641c}.head{display:flex;align-items:center;gap:14px;margin-bottom:22px}.head img{width:58px;height:58px;border-radius:50%;object-fit:cover}.title{font-size:24px;font-weight:700}.sub{margin-top:5px;color:#748198;font-size:13px}.row{display:grid;grid-template-columns:150px 150px 1fr;gap:12px;align-items:center;padding:13px 14px;margin-top:8px;border-radius:10px;background:#f7f9fc}.row b{color:#2563eb;font-size:16px}.row span{font-weight:600}.row small{color:#6b7280}.empty{padding:24px;text-align:center;color:#9aa5b5}</style><div class='card'><div class='head'><img src='" + escape(avatar) + "'><div><div class='title'>可用指令</div><div class='sub'>" + escape(nickname) + " · 根据当前权限展示</div></div></div>" + rows + "</div></html>";
    }

    private String commandMenuHtmlV2(String nickname, String avatarUri, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list) {
        StringBuilder rows = new StringBuilder();
        list.forEach(command -> rows.append("<div style='display:flex;align-items:center;gap:12px;padding:12px 14px;margin:8px 0;background:#f7f9fc;border:1px solid #e6ebf2;border-radius:10px;'>")
                .append("<div style='width:132px;color:#2563eb;font-size:16px;font-weight:700;'>/").append(escape(command.command())).append("</div>")
                .append("<div style='width:150px;color:#1f2937;font-weight:600;'>").append(escape(command.name())).append("</div>")
                .append("<div style='flex:1;color:#667085;font-size:13px;line-height:1.5;'>").append(escape(command.description())).append("</div></div>"));
        if (rows.isEmpty())
            rows.append("<div style='padding:24px;text-align:center;color:#98a2b3;'>暂无可用指令</div>");
        String initial = escape(nickname == null || nickname.isBlank() ? "访" : nickname.substring(0, 1));
        String avatar = avatarUri == null ? "<div style='width:54px;height:54px;border-radius:50%;background:#dbeafe;color:#2563eb;text-align:center;line-height:54px;font-size:24px;font-weight:700;'>" + initial + "</div>" : "<img src='" + escape(avatarUri) + "' style='width:54px;height:54px;border-radius:50%;object-fit:cover;'>";
        return "<html><body style='display:inline-block;margin:0;padding:16px;background:#f4f7fb;font-family:Arial,Microsoft YaHei,sans-serif;color:#1d2939;'><div id='command-menu-card' style='display:inline-block;min-width:520px;max-width:760px;box-sizing:border-box;padding:22px;background:#ffffff;border:1px solid #e4e7ec;border-radius:16px;'><div style='display:flex;align-items:center;gap:14px;padding-bottom:16px;border-bottom:1px solid #eef1f5;'>" + avatar + "<div><div style='font-size:22px;font-weight:700;line-height:1.3;'>可用指令</div><div style='margin-top:4px;color:#667085;font-size:13px;'>" + escape(nickname) + " · 根据当前权限展示</div></div></div><div style='padding-top:8px;'>" + rows + "</div></div></body></html>";
    }

    private String avatarDataUri(User user) {
        if (user == null || user.getQq() == null || user.getQq().getValue() == null) return null;
        try {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("https://q1.qlogo.cn/g?b=qq&nk=" + user.getQq().getValue() + "&s=100")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 == 2 && response.body().length > 0)
                return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(response.body());
        } catch (Exception ignored) {
        }
        return null;
    }

    private String commandMenuHtmlV3(String nickname, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list) {
        Map<String, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo>> groups = new java.util.LinkedHashMap<>();
        list.forEach(command -> groups.computeIfAbsent(command.pluginCode() == null || command.pluginCode().isBlank() ? "系统" : command.pluginCode(), ignored -> new java.util.ArrayList<>()).add(command));
        StringBuilder sections = new StringBuilder();
        String[] colors = {"#2563eb", "#7c3aed", "#0891b2", "#db2777", "#ea580c"};
        int[] colorIndex = {0};
        groups.forEach((plugin, commands) -> {
            String color = colors[colorIndex[0]++ % colors.length];
            StringBuilder cards = new StringBuilder();
            commands.forEach(command -> cards.append("<div style='padding:7px 0;border-top:1px solid #e5e7eb;line-height:1.55;'><span style='font-weight:700;color:#17202a;'>/").append(escape(command.command())).append("</span><span style='color:#344054;'> · ").append(escape(command.name())).append("</span><div style='font-size:12px;color:#667085;'>").append(escape(command.description())).append("</div></div>"));
            sections.append("<div style='padding:16px 18px;background:#fff;border:1px solid #d9dde3;border-radius:6px;box-shadow:0 1px 2px #00000008;'><div style='padding-bottom:10px;border-bottom:1px solid #d9dde3;font-size:22px;color:#009688;'>").append(escape(plugin)).append("</div><div style='padding-top:4px;'>").append(cards).append("</div></div>");
        });
        if (sections.isEmpty())
            sections.append("<div style='padding:28px;text-align:center;color:#98a2b3;'>暂时没有可用指令</div>");
        return "<html><body style='display:inline-block;margin:0;padding:10px;background:#f1f1f1;font-family:Arial,Microsoft YaHei,sans-serif;color:#182230;'><div id='command-menu-card' style='display:inline-block;width:720px;box-sizing:border-box;padding:10px;'><div style='text-align:center;margin-bottom:18px;'><span style='display:inline-block;padding:8px 16px;background:#009688;color:#fff;border-radius:5px;font-size:22px;font-weight:700;'>指令菜单</span></div><div style='margin:0 0 14px;padding:0 4px;color:#4b5563;font-size:13px;'>您好，" + escape(nickname) + "。以下是您当前有权限使用的指令：</div><div style='display:grid;grid-template-columns:1fr 1fr;gap:16px;align-items:start;'>" + sections + "</div><div style='margin-top:14px;font-size:11px;color:#98a2b3;text-align:right;'>" + escape(siteName()) + " · 权限菜单</div></div></body></html>";
    }

    private String commandMenuHtmlTemplate(String nickname, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list, boolean showDescription) {
        Map<String, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo>> groups = new java.util.LinkedHashMap<>();
        list.forEach(command -> {
            String pluginCode = command.pluginCode();
            String groupName = pluginCode == null || pluginCode.isBlank() ? "系统" : runtime.displayName(pluginCode);
            groups.computeIfAbsent(groupName, ignored -> new java.util.ArrayList<>()).add(command);
        });
        Context context = new Context();
        context.setVariable("nickname", nickname);
        context.setVariable("groups", groups);
        context.setVariable("siteName", siteName());
        context.setVariable("showDescription", showDescription);
        return templateEngine.process("plugin-command-menu", context);
    }

    /**
     * 指令菜单卡片落款使用系统设置的站点名称
     */
    private String siteName() {
        try {
            return settings.findByCategory("site").stream()
                    .filter(setting -> setting != null && "siteName".equals(setting.getKey()))
                    .map(online.yudream.base.domain.system.setting.aggregate.Setting::getValue)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("站点");
        } catch (Exception ignored) {
            return "站点";
        }
    }

    private String commandMenuHtmlV4(String nickname, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list) {
        Map<String, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo>> groups = new java.util.LinkedHashMap<>();
        list.forEach(command -> groups.computeIfAbsent(command.pluginCode() == null || command.pluginCode().isBlank() ? "系统" : command.pluginCode(), ignored -> new java.util.ArrayList<>()).add(command));
        StringBuilder sections = new StringBuilder();
        groups.forEach((plugin, commands) -> {
            StringBuilder rows = new StringBuilder();
            commands.forEach(command -> rows.append("<div style='padding:10px 0;border-top:1px solid #e4e7eb;line-height:1.65;'>")
                    .append("<span style='font-weight:700;color:#17202a;'>/").append(escape(command.command())).append("</span>")
                    .append("<span style='color:#344054;'> · ").append(escape(command.name())).append("</span>")
                    .append("<div style='margin-top:2px;font-size:12px;color:#667085;'>").append(escape(command.description())).append("</div></div>"));
            sections.append("<section style='display:block;box-sizing:border-box;margin:0 0 20px;padding:20px 22px;background:#fff;border:1px solid #d9dde3;border-radius:6px;box-shadow:0 1px 2px #00000008;break-inside:avoid;page-break-inside:avoid;'>")
                    .append("<div style='padding-bottom:12px;border-bottom:1px solid #d9dde3;font-size:22px;color:#009688;'>").append(escape(plugin)).append("</div>")
                    .append("<div style='padding-top:6px;'>").append(rows).append("</div></section>");
        });
        if (sections.isEmpty()) {
            sections.append("<div style='padding:32px;text-align:center;color:#98a2b3;'>暂无可用指令</div>");
        }
        return "<html><body style='display:inline-block;margin:0;padding:18px;background:#f1f1f1;font-family:Arial,Microsoft YaHei,sans-serif;color:#182230;'>"
                + "<div id='command-menu-card' style='display:inline-block;width:760px;box-sizing:border-box;padding:20px 0 18px;'>"
                + "<div style='text-align:center;margin-bottom:22px;'><span style='display:inline-block;padding:10px 20px;background:#009688;color:#fff;border-radius:5px;font-size:22px;font-weight:700;'>指令菜单</span></div>"
                + "<div style='margin:0 0 18px;padding:0 22px;color:#4b5563;font-size:13px;'>您好，" + escape(nickname) + "。以下是您当前有权限使用的指令：</div>"
                + "<div style='box-sizing:border-box;padding:0 22px;column-count:2;column-gap:20px;'>" + sections + "</div>"
                + "<div style='margin-top:2px;font-size:11px;color:#98a2b3;text-align:right;'>" + escape(siteName()) + " · 权限菜单</div></div></body></html>";
    }

    /**
     * 官方 QQ 机器人指令菜单。QQ markdown 不支持表格，使用标题分组 + 列表 + 引用 + 分割线的版式。
     * 主菜单只列指令名，二级菜单才附描述。
     */
    private String commandMenuMarkdown(String nickname, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list, boolean showDescription) {
        Map<String, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo>> groups = new java.util.LinkedHashMap<>();
        list.forEach(command -> {
            String pluginCode = command.pluginCode();
            String groupName = pluginCode == null || pluginCode.isBlank() ? "系统" : runtime.displayName(pluginCode);
            groups.computeIfAbsent(groupName, ignored -> new java.util.ArrayList<>()).add(command);
        });
        StringBuilder text = new StringBuilder("# 🤖 指令菜单\n\n您好，**").append(markdown(nickname)).append("**，以下是您当前可用的指令：\n\n");
        if (groups.isEmpty()) {
            text.append("> 暂无可用指令\n\n");
        }
        groups.forEach((plugin, commands) -> {
            text.append("## ").append(markdown(plugin)).append("\n\n");
            commands.forEach(command -> {
                text.append("- `/").append(markdown(command.command())).append("` **")
                        .append(markdown(command.name())).append("**");
                if (showDescription) {
                    text.append(" — ").append(markdown(command.description()));
                }
                text.append("\n");
            });
            text.append("\n");
        });
        text.append("***\n\n> ").append(markdown(siteName())).append(" · 菜单按当前权限展示");
        return text.toString();
    }

    private String markdown(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private boolean requiresBound() {
        return settings.findByKey("plugin.qq-binding.require-bound-qq")
                .map(setting -> Boolean.parseBoolean(setting.getValue()))
                .orElse(false);
    }

    /**
     * 判断指令是否为仅绑定用户可用（allowAnonymous=false）。
     */
    private boolean commandRequiresBound(String commandName) {
        return runtime.commands().stream()
                .anyMatch(command -> commandName.equalsIgnoreCase(command.command()) && !command.allowAnonymous());
    }

    /**
     * 未绑定用户使用需要绑定的指令时，给出明确提示而非静默忽略。
     */
    private void replyBindHint(PluginEvent event) {
        sendMenuText(event, "当前消息身份未绑定系统账号，请先完成绑定后再使用该指令。");
    }

    /** 官方全量群消息里的机器人回填指令名：/绑定机器人 六位回填码。 */
    static final String BIND_MENTION_COMMAND = "绑定机器人";

    /**
     * 官方全量群消息（GROUP_MESSAGE_CREATE）对每条消息推送，机器人在群内被 @ 的提及 openid
     * 无法自动得知：管理员在连接页生成短时回填码，在目标群 @机器人 发送「/绑定机器人 码」，
     * 用该消息内容的首个提及 id 回填连接配置并即时生效。回填码本身即授权，不要求已绑定系统账号。
     */
    private void bindBotMention(PluginEvent event, Parsed command, MilkyConnection connection) {
        if (connection == null || !connection.official()) {
            sendMenuText(event, "该指令仅支持官方 QQ 机器人连接。");
            return;
        }
        if (QqSandboxExecutionScope.current() != null) {
            sendMenuText(event, "沙箱模式不回填真实连接配置。");
            return;
        }
        List<String> mentions = OfficialQqBotEventNormalizer.mentionIdsFromContent(event.content());
        if (mentions.isEmpty()) {
            sendMenuText(event, "请先 @ 机器人再发送：/绑定机器人 六位回填码");
            return;
        }
        try {
            connectionApps.consumeMentionBindingCode(
                    command.arguments().isEmpty() ? "" : command.arguments().getFirst(), connection.getId());
        } catch (RuntimeException exception) {
            sendMenuText(event, exception.getMessage() == null ? "回填码校验失败" : exception.getMessage());
            return;
        }
        String mentionOpenId = mentions.getFirst();
        connection.bindMentionOpenId(mentionOpenId);
        connections.save(connection);
        officialSessions.rememberSelfAlias(connection.getId(), mentionOpenId);
        sendMenuText(event, "已记录机器人提及身份，现在 @ 机器人即可触发对话。");
    }

    private boolean allowed(User user, String permission) {
        return user != null && roles.findByIds(user.getRoles().stream().map(item -> item.getValue()).toList()).stream()
                .anyMatch(role -> role.getSystemType() == SystemRoleType.SUPER_ADMIN || role.hasPermission(permission));
    }

    static boolean isMenuAlias(String name) {
        return "菜单".equals(name) || "帮助".equals(name) || "菜单指令".equals(name);
    }

    static boolean isMessageEvent(String eventType) {
        return "message_receive".equals(eventType) || "message".equals(eventType);
    }

    static String messageUserId(Map<String, Object> data) {
        return firstText(data, "sender_id", "user_id");
    }

    static String messageChannelId(Map<String, Object> data) {
        return firstText(data, "peer_id", "group_id", "user_id");
    }

    static String messageContent(Map<String, Object> data) {
        return firstText(data, "segments", "message", "raw_message");
    }

    static Parsed parseCommand(String value) {
        if (value == null) {
            return null;
        }
        String source = stripLeadingMentions(value);
        if (source.isEmpty()) {
            return null;
        }
        if (isMenuAlias(source)) {
            return new Parsed(source, List.of());
        }
        if (!source.startsWith("/") && !source.startsWith("!")) {
            return null;
        }
        String[] parts = source.substring(1).trim().split("\\s+");
        return parts.length == 0 || parts[0].isBlank() ? null : new Parsed(parts[0], Arrays.stream(parts).skip(1).toList());
    }

    static String stripLeadingMentions(String value) {
        String source = value.trim();
        source = source.replaceAll("(?i)^(?:<@!?[^>]+>\\s*)+", "");
        source = source.replaceAll("(?i)^(?:\\[@?[A-Za-z0-9._-]{4,}\\]\\s*)+", "");
        source = source.replaceAll("(?i)^(?:@\\S+\\s+)+", "");
        return source.trim();
    }

    private static String text(Object value) {
        if (value instanceof List<?> parts) {
            StringBuilder content = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> map && "text".equals(String.valueOf(map.get("type")))
                        && map.get("data") instanceof Map<?, ?> data && data.get("text") != null) {
                    content.append(data.get("text"));
                }
            }
            return content.toString();
        }
        return value == null ? null : String.valueOf(value);
    }

    private static String firstText(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            String value = text(values.get(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static boolean officialDirectedAtBot(Map<String, Object> data) {
        if (data == null) {
            return false;
        }
        Object flag = data.get("mention_self");
        if (Boolean.TRUE.equals(flag) || "true".equalsIgnoreCase(String.valueOf(flag))) {
            return true;
        }
        String nativeType = text(data.get("native_type"));
        return "GROUP_AT_MESSAGE_CREATE".equals(nativeType)
                || "AT_MESSAGE_CREATE".equals(nativeType)
                || "C2C_MESSAGE_CREATE".equals(nativeType)
                || "DIRECT_MESSAGE_CREATE".equals(nativeType)
                || ("INTERACTION_CREATE".equals(nativeType) && Boolean.TRUE.equals(data.get("mention_self")));
    }

    static void copyOfficialReplyIds(Map<String, Object> data, Map<String, Object> referrer) {
        if (data == null || referrer == null) {
            return;
        }
        copyIfPresent(data, referrer, "message_scene");
        copyIfPresent(data, referrer, "msg_id");
        copyIfPresent(data, referrer, "event_id");
        copyIfPresent(data, referrer, "interaction_id");
        Object messageId = data.get("message_id");
        if (messageId != null && !String.valueOf(messageId).isBlank()) {
            referrer.putIfAbsent("message_id", String.valueOf(messageId));
            referrer.putIfAbsent("msg_id", String.valueOf(messageId));
        }
    }

    private static void copyIfPresent(Map<String, Object> data, Map<String, Object> referrer, String key) {
        Object value = data.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            referrer.putIfAbsent(key, String.valueOf(value));
        }
    }

    private MilkyConnection connectionOf(String connectionId) {
        Long id = parseConnectionId(connectionId);
        return id == null ? null : connections.findById(id).orElse(null);
    }

    private static Long parseConnectionId(String connectionId) {
        if (connectionId == null || connectionId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(connectionId.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String officialGroupOpenid(MilkyConnection connection, String scene, String channelId) {
        if (connection == null || !connection.official()) {
            return null;
        }
        if (!online.yudream.base.domain.system.user.service.MessagingIdentityClassifier.groupScene(scene)) {
            return null;
        }
        return channelId;
    }

    static List<String> mentionsFromSegments(Object value) {
        if (!(value instanceof List<?> parts)) return List.of();
        return parts.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(part -> "mention".equals(String.valueOf(part.get("type"))))
                .map(part -> part.get("data")).filter(Map.class::isInstance).map(Map.class::cast)
                .map(data -> data.get("user_id")).filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    private String replyMessageId(Object value) {
        if (!(value instanceof List<?> parts)) return null;
        for (Object part : parts) {
            if (!(part instanceof Map<?, ?> map) || !"reply".equals(String.valueOf(map.get("type"))) || !(map.get("data") instanceof Map<?, ?> data))
                continue;
            Object id = data.containsKey("message_id") ? data.get("message_id") : data.get("message_seq");
            if (id != null && !String.valueOf(id).isBlank()) return String.valueOf(id);
        }
        return null;
    }

    record Parsed(String name, List<String> arguments) {
    }

    record GroupRequest(String groupId, String userId, String requestId, String comment) {
    }
}
