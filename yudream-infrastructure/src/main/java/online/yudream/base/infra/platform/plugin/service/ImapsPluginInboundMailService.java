package online.yudream.base.infra.platform.plugin.service;

import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import online.yudream.base.infra.platform.mail.InboundMailContentMatcher;
import online.yudream.base.infra.platform.mail.service.InboundMailCapabilityProvider;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailMatch;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailQuery;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * 入站邮件匹配 SPI：只读扫描能力 Provider 配置的 IMAPS 收件箱最近若干封邮件，
 * 按实际发件域、验证码、关键词与时间窗匹配。验证码从主题、正文、HTML 与 PDF 附件抽取，
 * 比较时忽略空白与大小写。能力与配置由「平台能力 &gt; 入站邮箱」统一托管，
 * 本类不接触凭据落库，也不暴露邮件正文给插件。
 */
@Service
public class ImapsPluginInboundMailService implements PluginInboundMailService {

    private static final int MAX_MESSAGES = 50;
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 20000;

    private final ObjectProvider<InboundMailCapabilityProvider> capabilityProvider;
    private final InboundMailContentMatcher contentMatcher = new InboundMailContentMatcher();

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
        props.put("mail.imaps.connectiontimeout", String.valueOf(CONNECT_TIMEOUT_MS));
        props.put("mail.imaps.timeout", String.valueOf(READ_TIMEOUT_MS));
        props.put("mail.imaps.partialfetch", "false");
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
                Message[] messages = folder.getMessages(first, total);
                FetchProfile profile = new FetchProfile();
                profile.add(FetchProfile.Item.ENVELOPE);
                profile.add(FetchProfile.Item.CONTENT_INFO);
                folder.fetch(messages, profile);
                for (Message message : messages) {
                    long receivedAt = receivedAt(message);
                    if (query.receivedAfter() > 0 && receivedAt > 0 && receivedAt < query.receivedAfter()) {
                        continue;
                    }
                    try {
                        if (!contentMatcher.matches(message, query)) {
                            continue;
                        }
                    } catch (Exception ignored) {
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

    private static long receivedAt(Message message) {
        try {
            if (message.getReceivedDate() != null) {
                return message.getReceivedDate().getTime();
            }
            if (message.getSentDate() != null) {
                return message.getSentDate().getTime();
            }
        } catch (Exception ignored) {
        }
        return 0L;
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
