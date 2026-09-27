package online.yudream.base.infra.system.notification.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 站内通知数据对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Document(collection = "sysNotification")
@CompoundIndex(def = "{'userId': 1, 'read': 1, 'createdAt': -1}")
public class NotificationDO extends BaseDO {

    private Long userId;

    private String sourcePlugin;

    private String type;

    private String title;

    private String body;

    private String link;

    private boolean read;

    private LocalDateTime readAt;
}
