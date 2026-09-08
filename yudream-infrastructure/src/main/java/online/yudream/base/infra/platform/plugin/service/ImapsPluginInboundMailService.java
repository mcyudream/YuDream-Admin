package online.yudream.base.infra.platform.plugin.service;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import online.yudream.base.infra.platform.mail.service.InboundMailCapabilityProvider;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailMatch;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailQuery;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Properties;

/**
 * 入站邮件匹配 SPI：只读扫描能力 Provider 配置的 IMAPS 收件箱最近若干封邮件，
 * 按实际发件域、验证码、关键词与时间窗匹配。能力与配置由「平台能力 &gt; 入站邮箱」统一托管，
 * 本类不接触凭据落库，也不暴露邮件正文给插件。
 */
@Service
public class ImapsPluginInboundMailService implements PluginInboundMailService {

    private static final int MAX_MESSAGES = 50;
    private static final int MAX_TEXT_CHARS = 64_000;

    private final ObjectProvider<InboundMailCapabilityProvider> capabilityProvider;

    public ImapsPluginInboundMailService(ObjectProvider<InboundMailCapabilityProvider> capabilityProvider) {
        this.capabilityProvider = capabilityProvider;
    }

    @Override
    public boolean enabled() {
        InboundMailCapabilityProvider provider = provider();
        return provider != null && provider.active()
                && hasText(provider.configValue(InboundMailCapabilityProvider.CONFIG_HOST))
                && hasText(provider.configValue(InboundMailCapabilityProvider.CONFIG_USERNAME))
                && hasText(provider.configValue(InboundMailCapabilityProvider.CONFIG_PASSWORD));
    }

    @Override
    public String mailboxId() {
        InboundMailCapabilityProvider provider = provider();
        return provider == null ? "" : provider.configValue(InboundMailCapabilityProvider.CONFIG_MAILBOX_ID);
    }

    @Override
    public PluginInboundMailMatch match(PluginInboundMailQuery query) {
        InboundMailCapabilityProvider provider = provider();
        if (!enabled()) {
            return PluginInboundMailMatch.unavailable("宿主未配置入站邮件核验能力");
        }
        String mailboxId = provider.configValue(InboundMailCapabilityProvider.CONFIG_MAILBOX_ID);
        if (query == null || !mailboxId.equals(query.mailboxId()) || !hasText(query.verificationCode())) {
            return PluginInboundMailMatch.unavailable("入站邮件核验请求无效");
        }
        long now = System.currentTimeMillis();
        if (query.expiresAt() > 0 && now >= query.expiresAt()) {
            return PluginInboundMailMatch.pending("等待入站核验邮件已超时");
        }
        String folderName = provider.configValue(InboundMailCapabilityProvider.CONFIG_FOLDER);
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "5000");
        props.put("mail.imaps.timeout", "5000");
        try (Store store = Session.getInstance(props).getStore("imaps")) {
            store.connect(provider.configValue(InboundMailCapabilityProvider.CONFIG_HOST),
                    port(provider.configValue(InboundMailCapabilityProvider.CONFIG_PORT)),
                    provider.configValue(InboundMailCapabilityProvider.CONFIG_USERNAME),
                    provider.configValue(InboundMailCapabilityProvider.CONFIG_PASSWORD));
            Folder folder = store.getFolder(hasText(folderName) ? folderName : "INBOX");
            try {
                folder.open(Folder.READ_ONLY);
                int total = folder.getMessageCount();
                int first = Math.max(1, total - MAX_MESSAGES + 1);
                for (Message message : folder.getMessages(first, total)) {
                    long receivedAt = message.getReceivedDate() == null ? 0L : message.getReceivedDate().getTime();
                    if (receivedAt < query.receivedAfter()) {
                        continue;
                    }
                    if (!trustedSender(message, query) || !containsCodeAndKeywords(message, query)) {
                        continue;
                    }
                    return PluginInboundMailMatch.matched(receivedAt);
                }
                return PluginInboundMailMatch.pending("尚未收到匹配的入站核验邮件");
            } finally {
                if (folder.isOpen()) folder.close(false);
            }
        } catch (Exception ignored) {
            return PluginInboundMailMatch.unavailable("宿主暂时无法读取入站邮箱");
        }
    }

    private InboundMailCapabilityProvider provider() {
        return capabilityProvider == null ? null : capabilityProvider.getIfAvailable();
    }

    private boolean trustedSender(Message message, PluginInboundMailQuery query) throws Exception {
        if (query.allowedFromDomains().isEmpty()) return false;
        for (var address : message.getFrom() == null ? new jakarta.mail.Address[0] : message.getFrom()) {
            String value = address instanceof InternetAddress internet ? internet.getAddress() : address.toString();
            int at = value.lastIndexOf('@');
            if (at < 0) continue;
            String domain = value.substring(at + 1).toLowerCase(Locale.ROOT);
            if (query.allowedFromDomains().stream().anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsCodeAndKeywords(Message message, PluginInboundMailQuery query) throws Exception {
        String subject = message.getSubject() == null ? "" : message.getSubject();
        String body = String.valueOf(message.getContent());
        String text = (subject + "\n" + body).replaceAll("\\s+", " ");
        if (text.length() > MAX_TEXT_CHARS) text = text.substring(0, MAX_TEXT_CHARS);
        if (!text.contains(query.verificationCode())) return false;
        return query.requiredKeywords().stream().allMatch(text::contains);
    }

    private static int port(String raw) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            return value > 0 && value <= 65535 ? value : 993;
        } catch (NumberFormatException ignored) {
            return 993;
        }
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
