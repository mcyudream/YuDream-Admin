package online.yudream.base.interfaces.system.notification.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.notification.NotificationAppService;
import online.yudream.base.application.system.notification.NotificationAppService.NotificationCreatedEvent;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.notification.aggregate.Notification;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.notification.stream.NotificationSseHub;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 官方站内通知中心：REST 查询/已读 + SSE 实时推送流。
 * 面向登录用户本人的消息，读取即本人数据，无需管理权限。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationAppService notificationAppService;
    private final NotificationSseHub notificationSseHub;

    private Long currentUserId() {
        return SecurityPrincipalSupport.current().userId();
    }

    @GetMapping
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        Long userId = currentUserId();
        PageResult<Notification> result = notificationAppService.page(userId, page, Math.min(size, 100));
        return Result.ok(new PageResult<>(result.getRecords().stream().map(this::view).toList(), result.getTotal(), result.getPage(), result.getSize()));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> unreadCount() {
        return Result.ok(Map.of("unread", notificationAppService.unreadCount(currentUserId())));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationAppService.markRead(currentUserId(), id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Map<String, Object>> markAllRead() {
        Long userId = currentUserId();
        return Result.ok(Map.of("updated", notificationAppService.markAllRead(userId)));
    }

    /** SSE 实时推送流：通知创建时推送 {type:"notification"} 刷新信号。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        Long userId = currentUserId();
        SseEmitter emitter = notificationSseHub.register(userId);
        // 建立连接即推送一次当前未读数，前端无需额外首拉。
        notificationSseHub.push(userId, "unread", Map.of("unread", notificationAppService.unreadCount(userId)));
        return emitter;
    }

    /** 通知创建事件 → SSE 实时下发（未读数信号由前端收到后自行拉取列表）。 */
    @EventListener
    public void onNotificationCreated(NotificationCreatedEvent event) {
        notificationSseHub.push(event.userId(), "notification", Map.of("id", String.valueOf(event.notificationId()), "title", event.title() == null ? "" : event.title()));
    }

    private Map<String, Object> view(Notification notification) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(notification.getId()));
        view.put("sourcePlugin", notification.getSourcePlugin());
        view.put("type", notification.getType());
        view.put("title", notification.getTitle());
        view.put("body", notification.getBody());
        view.put("link", notification.getLink());
        view.put("read", notification.isRead());
        view.put("createdAt", notification.getCreatedAt() == null ? null : notification.getCreatedAt().toString());
        return view;
    }
}
