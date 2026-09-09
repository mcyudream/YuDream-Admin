# 入站邮箱

入站邮箱（能力 code：`inbound-mail`）以 IMAPS 只读方式连接收件箱。管理端可在「平台能力 > 入站邮箱」查看最近邮件与详情；插件按发件域、验证码与关键词匹配入站邮件。匹配会读取主题、纯文本/HTML 正文和 PDF 附件文本，验证码比较忽略空白与大小写。插件拿不到邮箱凭据或正文，只拿到核验结果。

项目闸门环境变量 `PLATFORM_INBOUND_MAIL_ENABLED`（默认 `true`）。启用前必须在能力页配置 IMAP 主机、用户名与密码（或授权码）。插件端口见 [MailSpi](/plugin/spi/v1/mail)。

## 能力配置

| 键 | 说明 | 默认 |
|---|---|---|
| `mailboxId` | 收件箱标识，字母数字点下划线短横线，最长 64 | `default` |
| `host` | IMAP 主机 | 空 |
| `port` | IMAP 端口 | `993` |
| `username` | 登录用户名 | 空 |
| `password` | 密码或授权码（加密落库） | 空 |
| `folder` | 文件夹 | `INBOX` |

连通性测试会连接并只读打开文件夹，返回邮件总数。启用后可在管理端「入站邮箱」菜单浏览邮件列表与详情（HTML 在沙箱 iframe 中预览，附件仅展示文件名与大小，不提供下载）。未启用时依赖入站邮件的插件功能应显式降级。

## 管理端接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/platform/inbound-mail/messages` | `platform:inbound-mail:view` | 分页列表，查询参数 `page`/`size`/`keyword`，`size` 最大 50 |
| GET | `/api/platform/inbound-mail/messages/{uid}` | `platform:inbound-mail:view` | 按 IMAP UID 读取详情（主题、收发件人、正文、附件元数据） |

列表按收件时间倒序。这是实时 IMAP 读取，不会把邮件持久化到数据库。插件 SPI 仍然只返回核验结果，不会拿到正文或凭据。

## 插件用法

```java
PluginInboundMailService inbound = context.framework().inboundMail();
if (!inbound.enabled()) {
    return; // 降级
}
PluginInboundMailMatch match = inbound.match(new PluginInboundMailQuery(
        inbound.mailboxId(),
        "123456",
        List.of("example.edu.cn"),
        List.of("验证码"),
        receivedAfter,
        expiresAt));
```

`status` 为 `MATCHED` / `PENDING` / `NOT_FOUND` / `UNAVAILABLE`。宿主不会把邮件正文或凭据回传给插件。
