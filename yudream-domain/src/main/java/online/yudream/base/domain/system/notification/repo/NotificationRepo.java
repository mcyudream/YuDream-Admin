package online.yudream.base.domain.system.notification.repo;

import online.yudream.base.domain.system.notification.aggregate.Notification;

import java.util.List;
import java.util.Optional;

/**
 * 站内通知仓储。
 */
public interface NotificationRepo {

    Notification save(Notification notification);

    Optional<Notification> findById(Long id);

    /** 按接收人分页读取（新→旧）。 */
    List<Notification> pageByUser(Long userId, int page, int size);

    long unreadCount(Long userId);

    /** 该用户通知总数。 */
    long countByUser(Long userId);

    /** 标记单条已读（校验归属）。 */
    boolean markRead(Long id, Long userId);

    /** 全部标记已读，返回受影响条数。 */
    int markAllRead(Long userId);
}
