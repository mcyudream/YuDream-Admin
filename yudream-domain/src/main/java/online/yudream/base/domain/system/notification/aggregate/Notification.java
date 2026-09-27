package online.yudream.base.domain.system.notification.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内通知聚合根：官方消息推送中心的最小消息单元，由插件或宿主自身
 * 推送，宿主前端铃铛按未读红点聚合展示。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    private Long id;

    /** 接收人用户 id。 */
    private Long userId;

    /** 来源插件 code（"system" 表示宿主自身）。 */
    private String sourcePlugin;

    /** 业务类型（如 reply/like/system），前端可差异化展示。 */
    private String type;

    private String title;

    private String body;

    /** 点击跳转的站内路径（可空）。 */
    private String link;

    private boolean read;

    private LocalDateTime createdAt;

    private LocalDateTime readAt;
}
