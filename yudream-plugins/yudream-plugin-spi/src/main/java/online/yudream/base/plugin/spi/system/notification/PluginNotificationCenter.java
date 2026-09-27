package online.yudream.base.plugin.spi.system.notification;

import java.util.List;
import java.util.Map;

/**
 * 宿主站内通知中心端口：插件把面向用户的消息推送进官方通知中心，
 * 由宿主前端铃铛（未读红点）与 SSE 实时推送统一呈现；通知持久化在宿主，
 * 已读/未读状态由宿主管理。
 */
public interface PluginNotificationCenter {

    /**
     * 发送一条站内通知，返回通知 id。
     *
     * @param sourcePlugin 来源插件 code（如 "forum"），前端用于图标与来源标记
     * @param type         业务类型（如 "reply"、"like"），前端可据此差异化展示
     * @param title        通知标题（一句话，建议含触发者与动作）
     * @param body         通知摘要正文（可空）
     * @param link         点击通知后的站内跳转路径（如 /platform/plugins/forum/post?id=1，可空）
     */
    String send(String sourcePlugin, long userId, String type, String title, String body, String link);

    /** 该用户未读通知数。 */
    int unreadCount(long userId);

    /**
     * 分页读取该用户通知（新→旧）。每条 Map 含：
     * id/type/title/body/link/sourcePlugin/read(boolean)/createdAt(long epoch)/readAt(long epoch, 0=未读)。
     */
    List<Map<String, Object>> list(long userId, int page, int size);

    /** 标记单条已读（仅本人通知生效）。 */
    void markRead(long userId, String id);

    /** 全部标记已读。 */
    void markAllRead(long userId);
}
