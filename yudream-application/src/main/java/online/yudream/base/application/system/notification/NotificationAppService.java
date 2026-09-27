package online.yudream.base.application.system.notification;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.notification.aggregate.Notification;
import online.yudream.base.domain.system.notification.repo.NotificationRepo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内通知中心应用服务：持久化 + 通过应用事件驱动 SSE 实时推送。
 */
@Service
@RequiredArgsConstructor
public class NotificationAppService {

    private final NotificationRepo notificationRepo;
    private final ApplicationEventPublisher eventPublisher;

    /** 通知创建事件：SSE Hub 监听后向在线接收人实时推送。 */
    public record NotificationCreatedEvent(Long userId, Long notificationId, String title) {
    }

    public String send(String sourcePlugin, Long userId, String type, String title, String body, String link) {
        if (userId == null) {
            throw new IllegalArgumentException("通知接收人不能为空");
        }
        Notification notification = notificationRepo.save(new Notification(
                null, userId,
                sourcePlugin == null || sourcePlugin.isBlank() ? "system" : sourcePlugin,
                type == null || type.isBlank() ? "system" : type,
                title == null ? "" : title,
                body == null ? "" : body,
                link == null ? "" : link,
                false, LocalDateTime.now(), null));
        eventPublisher.publishEvent(new NotificationCreatedEvent(userId, notification.getId(), notification.getTitle()));
        return String.valueOf(notification.getId());
    }

    public PageResult<Notification> page(Long userId, int page, int size) {
        return new PageResult<>(notificationRepo.pageByUser(userId, page, size), notificationRepo.countByUser(userId), page, size);
    }

    public int unreadCount(Long userId) {
        return (int) notificationRepo.unreadCount(userId);
    }

    public void markRead(Long userId, Long id) {
        notificationRepo.markRead(id, userId);
    }

    public int markAllRead(Long userId) {
        return notificationRepo.markAllRead(userId);
    }
}
