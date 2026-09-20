package online.yudream.base.infra.system.integration;

import online.yudream.base.domain.system.integration.valobj.MailServerConfig;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * 邮件发送器动态提供者：按「有效邮件配置」的值哈希缓存实例，
 * 管理端修改配置后下一次发送自动按新配置重建，无需重启。
 */
@Component
public class MailSenderProvider {

    private final SystemIntegrationSettings settings;

    private volatile JavaMailSenderImpl cached;
    private volatile int cachedHash = Integer.MIN_VALUE;

    public MailSenderProvider(SystemIntegrationSettings settings) {
        this.settings = settings;
    }

    public JavaMailSender sender() {
        return impl();
    }

    /** 当前有效发件人地址（配置 from 优先，回落账号名）。 */
    public String from() {
        return settings.effectiveMail().from();
    }

    private synchronized JavaMailSenderImpl impl() {
        MailServerConfig config = settings.effectiveMail();
        if (cached == null || cachedHash != config.hashCode()) {
            cached = MailSenderFactory.build(config);
            cachedHash = config.hashCode();
        }
        return cached;
    }
}
