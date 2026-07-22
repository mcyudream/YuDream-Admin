package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.milky.event.MilkyEventPublished;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
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
    private final UserRepo users;
    private final RoleRepo roles;
    private final SettingRepo settings;
    private final PluginCommandService commands;
    private final MilkyPluginMessagingService messaging;
    private final MilkyConnectionRepo connections;
    private final PluginRenderService renderer;
    private final FileAppService files;
    private final TemplateEngine templateEngine;

    static final long MENU_IMAGE_DEADLINE_SECONDS = 15;

    @EventListener
    public void dispatch(MilkyEventPublished published) {
        online.yudream.base.domain.platform.milky.model.MilkyModels.Event event = null;
        String messageSeq = null;
        try {
            event = published == null ? null : published.event();
            Map<String, Object> data = eventData(event);
            messageSeq = text(data.get("message_seq"));
            if (event == null) {
                return;
            }
            if ("group_request".equals(event.eventType()) || "group_join_request".equals(event.eventType())) {
                dispatchGroupRequest(published, data);
                return;
            }
            if (!isMessageEvent(event.eventType())) {
                return;
            }
            String userId = messageUserId(data);
            String channelId = messageChannelId(data);
            String content = messageContent(data);
            Map<String, Object> referrer = new java.util.LinkedHashMap<>();
            referrer.put("mentions", mentions(data.get("segments")));
            String replyMessageId = replyMessageId(data.get("segments"));
            if (replyMessageId != null) referrer.put("replyMessageId", replyMessageId);
            PluginEvent pluginEvent = new PluginEvent(String.valueOf(event.time()), event.eventType(), "milky", userId, channelId,
                    content, null, null, referrer, event.eventType(), data, String.valueOf(published.connectionId()),
                    event.selfId(), messageSeq);
            runtime.publishMessagingEvent(pluginEvent);

            Parsed command = parseCommand(content);
            if (command == null) {
                return;
            }
            User user = userId == null ? null : users.findByQQ(userId).orElse(null);
            if (isMenuAlias(command.name())) {
                menuImage(pluginEvent, user);
                return;
            }
            if (requiresBound() && user == null && !"绑定".equals(command.name())) {
                return;
            }
            runtime.publishCommand(pluginEvent, command.name(), command.arguments(), user == null ? null : user.getId(),
                    permission -> allowed(user, permission));
        } catch (Exception error) {
            log.error("Milky plugin event dispatch failed: connectionId={}, eventType={}, selfId={}, messageSeq={}, errorType={}",
                    published == null ? null : published.connectionId(), event == null ? null : event.eventType(),
                    event == null ? null : event.selfId(), messageSeq, error.getClass().getSimpleName());
        }
    }

    private void dispatchGroupRequest(MilkyEventPublished published, Map<String, Object> data) {
        var event = published.event();
        GroupRequest request = groupRequest(data);
        if (request == null) {
            log.warn("Ignoring incomplete Milky group request event: connectionId={}, eventType={}, selfId={}, messageSeq={}",
                    published.connectionId(), event.eventType(), event.selfId(), text(data.get("message_seq")));
            return;
        }
        Map<String, Object> referrer = new java.util.LinkedHashMap<>();
        referrer.put("requestId", request.requestId());
        String comment = request.comment();
        if (comment != null) {
            referrer.put("comment", comment);
        }
        PluginEvent pluginEvent = new PluginEvent(String.valueOf(event.time()), "group_request", "milky", request.userId(), request.groupId(),
                comment, null, null, referrer, event.eventType(), data, String.valueOf(published.connectionId()),
                event.selfId(), request.requestId());
        runtime.publishMessagingEvent(pluginEvent);
    }

    static Map<String, Object> eventData(online.yudream.base.domain.platform.milky.model.MilkyModels.Event event) {
        return event == null || event.data() == null ? Map.of() : event.data();
    }

    static GroupRequest groupRequest(Map<String, Object> data) {
        String groupId = firstText(data, "group_id", "group_uin", "peer_id");
        String userId = firstText(data, "user_id", "applicant_id", "initiator_id", "sender_id");
        String requestId = firstText(data, "request_id", "notification_seq", "flag", "id");
        return groupId == null || userId == null || requestId == null ? null
                : new GroupRequest(groupId, userId, requestId, firstText(data, "comment", "message", "verify_message"));
    }

    private CompletionStage<?> menu(PluginEvent event, User user) {
        StringBuilder content = new StringBuilder("可用指令：");
        commands.listAccessible(user == null ? null : user.getId()).forEach(command -> content
                .append("\n/").append(command.command()).append(" - ").append(command.description()));
        return sendMenuText(event, content.toString());
    }

    private void menuImage(PluginEvent event, User user) {
        AtomicBoolean fallbackStarted = new AtomicBoolean();
        try {
            var commandList = commands.listAccessible(user == null ? null : user.getId());
            String nickname = user == null ? "访客" : (user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname());
            CompletionStage<?> imageSend = renderer.html(commandMenuHtmlTemplate(nickname, commandList)).thenCompose(image -> {
                var connection = connections.findById(Long.valueOf(event.connectionId())).orElse(null);
                String mode = connection == null ? "base64" : connection.getCommandMenuImageMode();
                String uri = "url".equalsIgnoreCase(mode) ? uploadMenuImage(image, connection == null ? null : connection.getCommandMenuPublicBaseUrl()) : "base64://" + Base64.getEncoder().encodeToString(image.content());
                return messaging.send(new PluginMessageRequest(event.connectionId(), "qq", event.selfId(), event.channelId(),
                        new PluginMessageContent(PluginMessageContent.Type.IMAGE, uri, null, Map.of())));
            });
            fallbackOnMenuImageFailure(withMenuDeadline(imageSend), fallbackStarted, () -> menu(event, user), event);
        } catch (Exception error) {
            fallbackOnMenuImageFailure(failedStage(error), fallbackStarted, () -> menu(event, user), event);
        }
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
        imageSend.whenComplete((ignored, error) -> {
            if (error == null || !fallbackStarted.compareAndSet(false, true)) {
                return;
            }
            log.warn("Milky 菜单图片渲染或发送失败，降级为文本菜单: connectionId={}, channelId={}",
                    event.connectionId(), event.channelId(), error);
            try {
                fallback.get().whenComplete((fallbackIgnored, fallbackError) -> {
                    if (fallbackError != null) {
                        log.error("Milky 菜单文本降级发送失败: connectionId={}, channelId={}",
                                event.connectionId(), event.channelId(), fallbackError);
                    }
                });
            } catch (Exception fallbackError) {
                log.error("Milky 菜单文本降级构建失败: connectionId={}, channelId={}",
                        event.connectionId(), event.channelId(), fallbackError);
            }
        });
    }

    private CompletionStage<?> sendMenuText(PluginEvent event, String content) {
        return messaging.send(new PluginMessageRequest(event.connectionId(), "qq", event.selfId(), event.channelId(),
                new PluginMessageContent(PluginMessageContent.Type.TEXT, content, null, Map.of())));
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
        if (rows.isEmpty()) rows.append("<div style='padding:24px;text-align:center;color:#98a2b3;'>暂无可用指令</div>");
        String initial = escape(nickname == null || nickname.isBlank() ? "访" : nickname.substring(0, 1));
        String avatar = avatarUri == null ? "<div style='width:54px;height:54px;border-radius:50%;background:#dbeafe;color:#2563eb;text-align:center;line-height:54px;font-size:24px;font-weight:700;'>" + initial + "</div>" : "<img src='" + escape(avatarUri) + "' style='width:54px;height:54px;border-radius:50%;object-fit:cover;'>";
        return "<html><body style='display:inline-block;margin:0;padding:16px;background:#f4f7fb;font-family:Arial,Microsoft YaHei,sans-serif;color:#1d2939;'><div id='command-menu-card' style='display:inline-block;min-width:520px;max-width:760px;box-sizing:border-box;padding:22px;background:#ffffff;border:1px solid #e4e7ec;border-radius:16px;'><div style='display:flex;align-items:center;gap:14px;padding-bottom:16px;border-bottom:1px solid #eef1f5;'>" + avatar + "<div><div style='font-size:22px;font-weight:700;line-height:1.3;'>可用指令</div><div style='margin-top:4px;color:#667085;font-size:13px;'>" + escape(nickname) + " · 根据当前权限展示</div></div></div><div style='padding-top:8px;'>" + rows + "</div></div></body></html>";
    }

    private String avatarDataUri(User user) {
        if (user == null || user.getQq() == null || user.getQq().getValue() == null) return null;
        try {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("https://q1.qlogo.cn/g?b=qq&nk=" + user.getQq().getValue() + "&s=100")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 == 2 && response.body().length > 0) return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(response.body());
        } catch (Exception ignored) { }
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
        if (sections.isEmpty()) sections.append("<div style='padding:28px;text-align:center;color:#98a2b3;'>暂时没有可用指令</div>");
        return "<html><body style='display:inline-block;margin:0;padding:10px;background:#f1f1f1;font-family:Arial,Microsoft YaHei,sans-serif;color:#182230;'><div id='command-menu-card' style='display:inline-block;width:720px;box-sizing:border-box;padding:10px;'><div style='text-align:center;margin-bottom:18px;'><span style='display:inline-block;padding:8px 16px;background:#009688;color:#fff;border-radius:5px;font-size:22px;font-weight:700;'>指令菜单</span></div><div style='margin:0 0 14px;padding:0 4px;color:#4b5563;font-size:13px;'>您好，" + escape(nickname) + "。以下是您当前有权限使用的指令：</div><div style='display:grid;grid-template-columns:1fr 1fr;gap:16px;align-items:start;'>" + sections + "</div><div style='margin-top:14px;font-size:11px;color:#98a2b3;text-align:right;'>YuDream Admin · 权限菜单</div></div></body></html>";
    }

    private String commandMenuHtmlTemplate(String nickname, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list) {
        Map<String, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo>> groups = new java.util.LinkedHashMap<>();
        list.forEach(command -> {
            String pluginCode = command.pluginCode();
            String groupName = pluginCode == null || pluginCode.isBlank() ? "系统" : runtime.displayName(pluginCode);
            groups.computeIfAbsent(groupName, ignored -> new java.util.ArrayList<>()).add(command);
        });
        Context context = new Context();
        context.setVariable("nickname", nickname);
        context.setVariable("groups", groups);
        return templateEngine.process("plugin-command-menu", context);
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
                + "<div style='margin-top:2px;font-size:11px;color:#98a2b3;text-align:right;'>YuDream Admin · 权限菜单</div></div></body></html>";
    }

    private String commandMenuMarkdown(String nickname, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo> list) {
        Map<String, List<online.yudream.base.plugin.spi.system.command.PluginCommandInfo>> groups = new java.util.LinkedHashMap<>();
        list.forEach(command -> groups.computeIfAbsent(command.pluginCode() == null || command.pluginCode().isBlank() ? "系统" : command.pluginCode(), ignored -> new java.util.ArrayList<>()).add(command));
        StringBuilder text = new StringBuilder("# 指令菜单\n\n您好，**").append(markdown(nickname)).append("**。以下是您当前有权限使用的指令：\n\n");
        groups.forEach((plugin, commands) -> {
            text.append("## ").append(markdown(plugin)).append("\n\n| 指令 | 名称 | 用法 |\n| --- | --- | --- |\n");
            commands.forEach(command -> text.append("| `/").append(markdown(command.command())).append("` | ").append(markdown(command.name())).append(" | ").append(markdown(command.description())).append(" |\n"));
            text.append("\n");
        });
        text.append("---\n*YuDream Admin · 权限菜单*");
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

    private boolean allowed(User user, String permission) {
        return user != null && roles.findByIds(user.getRoles().stream().map(item -> item.getValue()).toList()).stream()
                .anyMatch(role -> role.hasPermission(permission));
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
        String source = value.trim();
        if (isMenuAlias(source)) {
            return new Parsed(source, List.of());
        }
        if (!source.startsWith("/") && !source.startsWith("!")) {
            return null;
        }
        String[] parts = source.substring(1).trim().split("\\s+");
        return parts.length == 0 || parts[0].isBlank() ? null : new Parsed(parts[0], Arrays.stream(parts).skip(1).toList());
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

    private List<String> mentions(Object value) {
        if (!(value instanceof List<?> parts)) return List.of();
        return parts.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(part -> "mention".equals(String.valueOf(part.get("type"))))
                .map(part -> part.get("data")).filter(Map.class::isInstance).map(Map.class::cast)
                .map(data -> data.get("user_id")).filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    private String replyMessageId(Object value) {
        if (!(value instanceof List<?> parts)) return null;
        for (Object part : parts) {
            if (!(part instanceof Map<?, ?> map) || !"reply".equals(String.valueOf(map.get("type"))) || !(map.get("data") instanceof Map<?, ?> data)) continue;
            Object id = data.containsKey("message_id") ? data.get("message_id") : data.get("message_seq");
            if (id != null && !String.valueOf(id).isBlank()) return String.valueOf(id);
        }
        return null;
    }

    record Parsed(String name, List<String> arguments) {
    }

    record GroupRequest(String groupId, String userId, String requestId, String comment) { }
}
