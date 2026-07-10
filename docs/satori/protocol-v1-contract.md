# Satori v1 协议契约清单

本清单以 Satori v1 中文协议文档为准，用于后续 HTTP、WebSocket 和插件 SPI 的契约测试。

## 边界规则

- JSON 中的 `id`、`self_id`、`parent_id`、分页游标和 WebSocket `sn` 一律以字符串传输。
- 时间戳、颜色和角色位置保留数值语义；可选字段允许缺失或为 `null`。
- 标准字段使用 `snake_case`，原生扩展事件使用 `_type` 与 `_data` 原样透传。

## 标准资源

| 资源 | 字段 |
| --- | --- |
| Login | `platform`、`self_id`、`user`、`status`、`adapter`、`features` |
| Event | `id`、`type`、`platform`、`self_id`、`timestamp`、资源上下文、`_type`、`_data`；WebSocket 信封附加 `sn` |
| Message | `id`、`content`、`channel`、`guild`、`member`、`user`、`created_at`、`updated_at` |
| Channel | `id`、`type`、`name`、`parent_id` |
| Guild | `id`、`name`、`avatar` |
| GuildMember | `user`、`nick`、`avatar`、`joined_at` |
| GuildRole | `id`、`name`、`color`、`position` |
| Friend | `user`、`nick`、`remark` |
| Emoji | `id`、`name`、`url` |
| User | `id`、`name`、`nick`、`avatar`、`is_bot` |
| Argv | `name`、`arguments`、`options` |
| Button | `id` |
| Meta | 协议与适配器元信息；未知字段保持向前兼容 |
| List | `data`、`next` |
| BidiList | `data`、`prev`、`next` |

## HTTP API

| 分组 | 方法 |
| --- | --- |
| 消息 | `message.create`、`message.get`、`message.delete`、`message.update`、`message.list` |
| 表态 | `message.reaction.create`、`message.reaction.delete`、`message.reaction.list` |
| 频道 | `channel.get`、`channel.list`、`channel.create`、`channel.update`、`channel.delete` |
| 群组 | `guild.get`、`guild.list` |
| 成员 | `guild.member.get`、`guild.member.list`、`guild.member.kick`、`guild.member.mute` |
| 角色 | `guild.role.list`、`guild.role.create`、`guild.role.update`、`guild.role.delete` |
| 登录与用户 | `login.get`、`user.get` |
| 好友 | `friend.list`、`friend.approve` |
| 文件与元数据 | `upload.create`、`meta` |
| 原生扩展 | `internal`，仅对经授权的平台专属能力开放 |

## WebSocket

- 操作码：`EVENT(0)`、`PING(1)`、`PONG(2)`、`IDENTIFY(3)`、`READY(4)`、`META(5)`。
- 客户端应在连接建立后发送 `IDENTIFY`，保持心跳，并在重连时携带最后成功处理的字符串 `sn`。
- `READY` 提供登录账号，`META` 更新适配器元数据，`EVENT` 按序持久化、发布并推进游标。

## 实验性和扩展

- 适配器的 `_type`、`_data` 及 `internal` 请求是扩展边界，必须保留原始数据，不得由通用模型猜测其结构。
- 不在标准能力集合中的接口、元素与事件一律按实验性或平台专属能力处理，并由 `Login.features` 决定是否可用。
