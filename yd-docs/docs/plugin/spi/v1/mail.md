# 邮件 MailSpi

> SPI v1 · 当前源码 2.24.0 · 包 `online.yudream.base.plugin.spi.system.mail`

`context.framework().mail()` 返回 `PluginMailService`，是插件发送邮件的唯一端口。宿主负责 SMTP 连接、编码与异步投递，插件只构造 `PluginMailMessage` 并调用 `send`。

## PluginMailService

```java
public interface PluginMailService {

    void send(PluginMailMessage message);
}
```

| 方法 | 签名 | 说明 |
|---|---|---|
| `send` | `void send(PluginMailMessage message)` | 发送一封邮件；入参校验失败抛 `IllegalArgumentException` |

## PluginMailMessage

```java
public record PluginMailMessage(
        String from,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String text,
        String html
)
```

紧凑构造器把 null 的 `to`/`cc`/`bcc` 归一为空列表并做防御性拷贝，因此 record 字段永远非 null。

| 字段 | 类型 | 说明 |
|---|---|---|
| `from` | `String` | 发件人地址；传 `null` 时使用宿主邮件配置的默认发件人 |
| `to` | `List<String>` | 收件人列表，**至少一个，否则被宿主拒绝** |
| `cc` / `bcc` | `List<String>` | 抄送 / 密送列表 |
| `subject` | `String` | 主题，**不能为空白** |
| `text` | `String` | 纯文本正文（与 `html` 至少提供一个） |
| `html` | `String` | HTML 正文 |

### 静态工厂

| 工厂 | 等价构造 | 说明 |
|---|---|---|
| `PluginMailMessage.text(to, subject, text)` | `from=null`、`cc/bcc=空`、`html=null` | 纯文本邮件 |
| `PluginMailMessage.html(to, subject, html)` | `from=null`、`cc/bcc=空`、`text=null` | HTML 邮件 |

需要抄送、密送或自定义发件人时使用全参构造器。

## 使用示例

```java
@Override
public void onEnable(PluginContext context) {
    // 纯文本：发件人留空走宿主默认
    context.framework().mail().send(
            PluginMailMessage.text(List.of("ops@example.com"), "部署完成", "demo-plugin 已启用"));

    // HTML + 抄送 + 密送：全参构造
    context.framework().mail().send(new PluginMailMessage(
            null,                                  // from：null = 宿主默认发件人
            List.of("user@example.com"),
            List.of("reviewer@example.com"),
            List.of("audit@example.com"),
            "周报已生成",
            "请查收附件",
            "<p>周报已生成，<b>点击查看</b>。</p>"));
}
```

## 注意事项

- **校验规则**：`to` 为空或 `subject` 为空白时抛 `IllegalArgumentException`（错误文案为中文），应在调用前自行兜底。
- **异步投递**：`send()` 返回不代表已送达——宿主实现将真实 SMTP 调用放入独立线程池执行，发送失败仅记录日志，不会回调插件。需要确认送达结果的场景应改为轮询收件箱或改用站内消息通道。
- **from 语义**：只有显式传入地址才覆盖默认发件人；伪造第三方域名发件可能被 SMTP 服务商拒信。
- 邮件内容中的换行在纯文本模式下原样保留；HTML 模式由宿主以 UTF-8 发送。
- 该能力依赖宿主邮件配置可用；未配置 SMTP 时调用会在后台发送阶段失败，建议仅在配置完成后触发（如用户主动操作）。

## 入站核验 PluginInboundMailService

`context.framework().inboundMail()` 返回入站邮箱端口（能力码 `inbound-mail`）。默认实现 `enabled()` 恒 false。插件拿不到凭据或正文。产品说明见 [入站邮箱](/features/inbound-mail)。

| 方法 | 签名 | 说明 |
|---|---|---|
| `enabled` | `default boolean enabled()` | 能力是否已启用 |
| `mailboxId` | `default String mailboxId()` | 当前收件箱标识 |
| `match` | `default PluginInboundMailMatch match(PluginInboundMailQuery query)` | 只读匹配 |

`PluginInboundMailQuery(mailboxId, verificationCode, allowedFromDomains, requiredKeywords, receivedAfter, expiresAt)`。宿主从主题、正文、HTML 与 PDF 附件抽取文本后匹配验证码（忽略空白与大小写）和关键词，不把正文回传给插件。

`PluginInboundMailMatch(status, receivedAt, message)` 的 `status`：`MATCHED` / `PENDING` / `NOT_FOUND` / `UNAVAILABLE`。

---

> 源码引用：
> - SPI 契约：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/mail/`
> - 宿主适配：`yudream-infrastructure/.../infra/platform/plugin/service/PluginMailFrameworkService.java`、`.../infra/system/mail/impl/AsyncMailSender.java`
