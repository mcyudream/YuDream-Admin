package online.yudream.base.infra.system.notification;

import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.notification.aggregate.Notification;
import online.yudream.base.domain.system.notification.repo.NotificationRepo;
import online.yudream.base.infra.system.notification.dataobj.NotificationDO;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 站内通知仓储实现。
 */
@Service
public class NotificationRepoImpl implements NotificationRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    public NotificationRepoImpl(MongoTemplate mongoTemplate, IdGenerator idGenerator) {
        this.mongoTemplate = mongoTemplate;
        this.idGenerator = idGenerator;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationDO notificationDO = toDataObj(notification);
        if (notificationDO.getId() == null) {
            notificationDO.setId(idGenerator.nextId());
            notificationDO.setCreateTime(LocalDateTime.now());
        }
        notificationDO.setRead(notification.isRead());
        notificationDO.setReadAt(notification.isRead() && notification.getReadAt() == null ? LocalDateTime.now() : notification.getReadAt());
        return toDomain(mongoTemplate.save(notificationDO));
    }

    @Override
    public Optional<Notification> findById(Long id) {
        return Optional.ofNullable(mongoTemplate.findById(id, NotificationDO.class)).map(this::toDomain);
    }

    @Override
    public List<Notification> pageByUser(Long userId, int page, int size) {
        Query query = Query.query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) Math.max(0, page - 1) * size)
                .limit(size);
        return mongoTemplate.find(query, NotificationDO.class).stream().map(this::toDomain).toList();
    }

    @Override
    public long unreadCount(Long userId) {
        return mongoTemplate.count(Query.query(Criteria.where("userId").is(userId).and("read").is(false)), NotificationDO.class);
    }

    @Override
    public long countByUser(Long userId) {
        return mongoTemplate.count(Query.query(Criteria.where("userId").is(userId)), NotificationDO.class);
    }

    @Override
    public boolean markRead(Long id, Long userId) {
        var result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(id).and("userId").is(userId).and("read").is(false)),
                Update.update("read", true).set("readAt", LocalDateTime.now()),
                NotificationDO.class);
        return result.getModifiedCount() > 0;
    }

    @Override
    public int markAllRead(Long userId) {
        var result = mongoTemplate.updateMulti(
                Query.query(Criteria.where("userId").is(userId).and("read").is(false)),
                Update.update("read", true).set("readAt", LocalDateTime.now()),
                NotificationDO.class);
        return (int) result.getModifiedCount();
    }

    private NotificationDO toDataObj(Notification notification) {
        NotificationDO notificationDO = new NotificationDO();
        notificationDO.setId(notification.getId());
        notificationDO.setUserId(notification.getUserId());
        notificationDO.setSourcePlugin(notification.getSourcePlugin());
        notificationDO.setType(notification.getType());
        notificationDO.setTitle(notification.getTitle());
        notificationDO.setBody(notification.getBody());
        notificationDO.setLink(notification.getLink());
        notificationDO.setRead(notification.isRead());
        notificationDO.setReadAt(notification.getReadAt());
        notificationDO.setCreateTime(notification.getCreatedAt());
        return notificationDO;
    }

    private Notification toDomain(NotificationDO notificationDO) {
        if (notificationDO == null) {
            return null;
        }
        Notification notification = new Notification();
        notification.setId(notificationDO.getId());
        notification.setUserId(notificationDO.getUserId());
        notification.setSourcePlugin(notificationDO.getSourcePlugin());
        notification.setType(notificationDO.getType());
        notification.setTitle(notificationDO.getTitle());
        notification.setBody(notificationDO.getBody());
        notification.setLink(notificationDO.getLink());
        notification.setRead(notificationDO.isRead());
        notification.setCreatedAt(notificationDO.getCreateTime());
        notification.setReadAt(notificationDO.getReadAt());
        return notification;
    }
}
