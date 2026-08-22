# Satori v1 协议契约清单

本清单以 Satori v1 中文协议文档为准，用于后续 HTTP、WebSocket、WebHook、消息投递和插件 SPI 的契约测试。

## 边界规则

- JSON 中的全部资源 ID、`self_id`、`parent_id`、分页游标和 WebSocket `sn` 一律以字符串传输。
- 时间戳、频道类型、登录状态和 Opcode 保留数值 wire value；可选字段允许缺失或为 `null`。
- 标准字段使用 `snake_case`，原生扩展事件使用 `_type` 与 `_data` 原样透传。
- `Meta` 至少保留 `impl`、`protocol_version`、`adapter`、`features`，并保留未知字段以支持前向兼容。

## 标准资源

| 资源 | 字段 |
| --- | --- |
| Login | `sn`（实验性）、`platform`、`user`、`status`、`adapter`（实验性）、`features`（实验性） |
| Event | `sn`、`type`、`timestamp`、`login`、`argv`、`button`、`channel`、`emoji`、`friend`、`guild`、`member`、`message`、`operator`、`role`、`user`、`referrer`（实验性）、`_type`、`_data` |
| Message | `id`、`content`、`channel`、`guild`、`member`、`user`、`created_at`、`updated_at` |
| Channel | `id`、`type`（TEXT=0、DIRECT=1、CATEGORY=2、VOICE=3）、`name`、`parent_id` |
| Guild | `id`、`name`、`avatar` |
| GuildMember | `user`、`nick`、`avatar`、`joined_at`、`roles` |
| GuildRole | `id`、`name` |
| Friend | `user`、`nick` |
| Emoji | `id`、`name` |
| User | `id`、`name`、`nick`、`avatar`、`is_bot` |
| Argv | `name`、`arguments`、`options` |
| Button | `id` |
| Meta | `impl`、`protocol_version`、`adapter`、`features`、`logins`、`proxy_urls` |
| List | `data`、`next` |
| BidiList | `data`、`prev`、`next` |

## HTTP API

所有标准 API 使用 `POST /v1/{resource}.{method}`，请求和响应为 JSON；`upload.create` 使用 multipart/form-data。除 `meta`、`proxy` 外，请求包含 `Satori-Platform` 和 `Satori-User-ID`。

| 方法 | 请求参数 | 响应 |
| --- | --- | --- |
| `channel.get` | `channel_id` | Channel |
| `channel.list` | `guild_id`、`next?` | List&lt;Channel&gt; |
| `channel.create` | `guild_id`、`data` | Channel |
| `channel.update` | `channel_id`、`data` | void |
| `channel.delete` | `channel_id` | void |
| `channel.mute`（实验性） | `channel_id`、`duration` | void |
| `user.channel.create` | `user_id`、`guild_id?` | Channel |
| `message.create` | `channel_id`、`content`、`referrer?`（实验性） | Message[] |
| `message.get` | `channel_id`、`message_id` | Message |
| `message.delete` | `channel_id`、`message_id` | void |
| `message.update` | `channel_id`、`message_id`、`content` | void |
| `message.list` | `channel_id`、`next?`、`direction?`、`limit?`、`order?` | BidiList&lt;Message&gt; |
| `user.get` | `user_id` | User |
| `guild.get` | `guild_id` | Guild |
| `guild.list` | `next?` | List&lt;Guild&gt; |
| `guild.approve` | `message_id`、`approve`、`comment?` | void |
| `guild.member.get` | `guild_id`、`user_id` | GuildMember |
| `guild.member.list` | `guild_id`、`next?` | List&lt;GuildMember&gt; |
| `guild.member.kick` | `guild_id`、`user_id`、`permanent?` | void |
| `guild.member.mute`（实验性） | `guild_id`、`user_id`、`duration` | void |
| `guild.member.approve` | `message_id`、`approve`、`comment?` | void |
| `guild.member.role.set` | `guild_id`、`user_id`、`role_id` | void |
| `guild.member.role.unset` | `guild_id`、`user_id`、`role_id` | void |
| `guild.role.list` | `guild_id`、`next?` | List&lt;GuildRole&gt; |
| `guild.role.create` | `guild_id`、`role` | GuildRole |
| `guild.role.update` | `guild_id`、`role_id`、`role` | void |
| `guild.role.delete` | `guild_id`、`role_id` | void |
| `friend.list` | `next?` | List&lt;Friend&gt; |
| `friend.delete` | `user_id` | void |
| `friend.approve` | `message_id`、`approve`、`comment?` | void |
| `reaction.create`（实验性） | `channel_id`、`message_id`、`emoji_id` | void |
| `reaction.delete`（实验性） | `channel_id`、`message_id`、`emoji_id`、`user_id?` | void |
| `reaction.clear`（实验性） | `channel_id`、`message_id`、`emoji_id?` | void |
| `reaction.list`（实验性） | `channel_id`、`message_id`、`emoji_id`、`next?` | List&lt;User&gt; |
| `login.get` | 无 | Login |
| `upload.create`（实验性） | multipart 文件字段 | `Map&lt;String, String&gt;` |
| `meta`（实验性） | 无 | Meta |
| `meta/webhook.create`（可选、实验性） | `url`、`token?` | void |
| `meta/webhook.delete`（可选、实验性） | `url` | void |

`/v1/proxy/{url}` 是资源代理路由；`/v1/internal/{method}` 为平台原生 API 逃生口，必须显式授权。

## 事件

| 资源 | 标准事件 |
| --- | --- |
| Login | `login-added`、`login-removed`、`login-updated` |
| Channel | `channel-added`、`channel-updated`、`channel-removed` |
| Message | `message-created`、`message-updated`、`message-deleted` |
| Guild | `guild-added`、`guild-updated`、`guild-removed`、`guild-request` |
| GuildMember | `guild-member-added`、`guild-member-updated`、`guild-member-removed`、`guild-member-request` |
| GuildRole | `guild-role-created`、`guild-role-updated`、`guild-role-deleted` |
| Emoji（实验性） | `guild-emoji-added`、`guild-emoji-updated`、`guild-emoji-deleted` |
| Friend | `friend-request` |
| Reaction（实验性） | `reaction-added`、`reaction-removed` |
| Interaction（实验性） | `interaction/button`、`interaction/command` |
| 平台原生 | `internal`，并使用 `_type` 和 `_data` 保留原始事件 |

## WebSocket 与 WebHook

- 操作码：`EVENT(0)`、`PING(1)`、`PONG(2)`、`IDENTIFY(3)`、`READY(4)`、`META(5)`。
- 连接建立后 10 秒内发送 IDENTIFY，随后每 10 秒发送 PING；重连时携带最后成功处理的字符串 `sn`。
- READY 返回 `logins` 与 `proxy_urls`；META 更新 `proxy_urls`；EVENT 以 `connectionId + sn` 幂等持久化后发布。
- WebHook 仅承载 EVENT/META，通过 `Satori-Opcode` 指定 opcode；反向鉴权使用 `Authorization: Bearer {token}`。

## 消息元素与平台特性

- 基础元素：`at`、`sharp`、`emoji`、`a`。
- 资源元素：`img`、`audio`、`video`、`file`。
- 修饰元素：`b/strong`、`i/em`、`u/ins`、`s/del`、`spl`、`code`、`sup`、`sub`。
- 排版、元信息和交互元素：`br`、`p`、`message`、`quote`、`author`、`button`（实验性）。
- `Login.features` 是标准 API 可用性的运行时事实；adapter 命名空间元素和属性属于平台专属扩展。
