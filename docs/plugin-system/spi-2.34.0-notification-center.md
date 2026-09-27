# SPI 2.34.0 — 官方站内通知中心端口

SPI 2.34.0 起，插件可把面向用户的消息推送进宿主官方站内通知中心：宿主前端顶栏
铃铛（未读红点）与 SSE 实时推送统一呈现，通知持久化在宿主，已读/未读状态由宿主
管理。插件不再需要自带"我的消息"页或轮询接口。

## 端口声明

`FrameworkServices.notifications()` 返回 `PluginNotificationCenter`：

```java
public interface PluginNotificationCenter {
    String send(String sourcePlugin, long userId, String type, String title, String body, String link);
    int unreadCount(long userId);
    List<Map<String, Object>> list(long userId, int page, int size);
    void markRead(long userId, String id);
    void markAllRead(long userId);
}
```

- `send(...)`：发送一条站内通知，返回通知 id（字符串化的 Snowflake）。
  - `sourcePlugin`：来源插件 code（如 `"forum"`），前端用于图标与来源标记；
  - `type`：业务类型（如 `"reply"`、`"like"`），前端可据此差异化展示；
  - `title`：一句话标题，建议含触发者与动作；`body`：摘要正文（可空）；
  - `link`：点击通知后的站内跳转路径（如 `/platform/plugins/forum/post?id=1`，可空）。
- `list(userId, page, size)`：按用户分页读取（新→旧）。每条 Map 含
  `id`（string）/ `type` / `title` / `body` / `link` / `sourcePlugin` / `read`(boolean) /
  `createdAt`(long epoch ms) / `readAt`(long epoch ms，0=未读)。
- `markRead(userId, id)` / `markAllRead(userId)`：已读操作只对本人通知生效。

## 兼容性与降级

- `notifications()` 是 default 方法：旧宿主（SPI 过旧）上返回的默认实现
  `send` 抛 `UnsupportedOperationException`，其余只读方法返回空值。
  插件应按软依赖模式降级（调用前 try/catch 或能力探测），不得硬依赖。
- 插件端只允许经 `context.frameworkServices().notifications()` 调用；
  禁止绕过端口直接写宿主集合或自建 HTTP 客户端。

## 宿主呈现

- REST：`GET /api/notifications`（分页）、`GET /api/notifications/unread-count`、
  `POST /api/notifications/{id}/read`、`POST /api/notifications/read-all`，
  均为登录用户本人数据。
- SSE：`GET /api/notifications/stream`（裸 token 鉴权，与宿主其他 SSE 一致），
  连接建立即推送 `unread` 帧；通知创建时推送 `notification` 帧，
  前端收到后自行刷新列表与未读数。
- 前端顶栏铃铛由工具栏设置项 `toolbar.notifications`（默认开启）控制显隐。
