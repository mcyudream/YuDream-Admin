package online.yudream.base.infra.system.integration;

import online.yudream.base.domain.system.integration.valobj.MailServerConfig;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * 邮件发送器构建工厂：按 MailServerConfig 构建独立的 Spring JavaMailSender 实例。
 * 动态提供者与候选配置探测共用，保证测试与真实发送行为一致。
 */
public final class MailSenderFactory {

    private static final String TIMEOUT_MILLIS = "5000";

    private MailSenderFactory() {
    }

    public static JavaMailSenderImpl build(MailServerConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        if (StringUtils.hasText(config.username())) {
            sender.setUsername(config.username());
        }
        if (StringUtils.hasText(config.password())) {
            sender.setPassword(config.password());
        }
        sender.setDefaultEncoding("UTF-8");
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", String.valueOf(config.ssl()));
        props.put("mail.smtp.starttls.enable", String.valueOf(config.starttls()));
        props.put("mail.smtp.connectiontimeout", TIMEOUT_MILLIS);
        props.put("mail.smtp.timeout", TIMEOUT_MILLIS);
        props.put("mail.smtp.writetimeout", TIMEOUT_MILLIS);
        return sender;
    }
}
