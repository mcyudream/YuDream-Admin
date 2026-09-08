package online.yudream.base.infra.platform.mail.service;

import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 入站邮箱能力 Provider：为插件提供只读收件箱匹配（核验邮件、通知回调等场景）。
 * 部署侧由环境变量 {@code PLATFORM_INBOUND_MAIL_ENABLED}
 * （{@code yudream.platform.capabilities.inbound-mail.enabled}）决定本 Bean 是否存在，
 * 运行侧由管理后台「平台能力 &gt; 入站邮箱」的启用开关与配置（入库，password 加密）决定运行时是否生效。
 * 环境变量 {@code MAIL_INBOUND_*} 仅作为首次保存配置前的默认值兜底。
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "yudream.platform.capabilities.inbound-mail", name = "enabled", havingValue = "true")
public class InboundMailCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "inbound-mail";
    public static final String CONFIG_MAILBOX_ID = "mailboxId";
    public static final String CONFIG_HOST = "host";
    public static final String CONFIG_PORT = "port";
    public static final String CONFIG_USERNAME = "username";
    public static final String CONFIG_PASSWORD = "password";
    public static final String CONFIG_FOLDER = "folder";

    private final Environment environment;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile Map<String, String> config = Map.of();

    public InboundMailCapabilityProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "入站邮箱",
                CapabilityType.INTEGRATION,
                "以 IMAPS 只读方式连接收件箱，供插件按发件域、验证码与关键词匹配入站邮件；未启用时依赖入站邮件的插件功能不可用",
                "i-ri:mail-download-line",
                40,
                Map.of(
                        CONFIG_MAILBOX_ID, env("MAIL_INBOUND_MAILBOX_ID", "default"),
                        CONFIG_HOST, env("MAIL_INBOUND_HOST", ""),
                        CONFIG_PORT, env("MAIL_INBOUND_PORT", "993"),
                        CONFIG_USERNAME, env("MAIL_INBOUND_USERNAME", ""),
                        CONFIG_PASSWORD, "",
                        CONFIG_FOLDER, env("MAIL_INBOUND_FOLDER", "INBOX")
                )
        );
    }

    @Override
    public CapabilityHealth health() {
        if (!enabled.get()) {
            return CapabilityHealth.disabled("入站邮箱能力未启用");
        }
        return CapabilityHealth.enabled("入站邮箱能力已启用",
                Map.of("mailboxId", configValue(CONFIG_MAILBOX_ID), "host", configValue(CONFIG_HOST)));
    }

    @Override
    public void enable(Map<String, String> config) {
        Map<String, String> merged = mergedConfig(config);
        String mailboxId = merged.get(CONFIG_MAILBOX_ID);
        if (!StringUtils.hasText(mailboxId) || !mailboxId.matches("[a-zA-Z0-9._-]{1,64}")) {
            throw new BizException("收件箱标识只能包含字母、数字、点、下划线和短横线，且不超过 64 个字符");
        }
        if (!StringUtils.hasText(merged.get(CONFIG_HOST))) {
            throw new BizException("启用入站邮箱能力前请先配置 IMAP 主机（host）");
        }
        if (!StringUtils.hasText(merged.get(CONFIG_USERNAME))) {
            throw new BizException("启用入站邮箱能力前请先配置用户名（username）");
        }
        if (!StringUtils.hasText(merged.get(CONFIG_PASSWORD))) {
            throw new BizException("启用入站邮箱能力前请先配置密码或授权码（password）");
        }
        int port = port(merged.get(CONFIG_PORT));
        if (port <= 0 || port > 65535) {
            throw new BizException("端口必须在 1-65535 之间");
        }
        this.config = merged;
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    /** IMAPS 连通性测试：连接并只读打开文件夹，返回邮件总数。 */
    @Override
    public CapabilityTestResult test(String message) {
        if (!enabled.get()) {
            return CapabilityTestResult.failure("入站邮箱能力未启用");
        }
        long started = System.currentTimeMillis();
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "5000");
        props.put("mail.imaps.timeout", "5000");
        try (Store store = Session.getInstance(props).getStore("imaps")) {
            store.connect(configValue(CONFIG_HOST), port(configValue(CONFIG_PORT)),
                    configValue(CONFIG_USERNAME), configValue(CONFIG_PASSWORD));
            Folder folder = store.getFolder(StringUtils.hasText(configValue(CONFIG_FOLDER)) ? configValue(CONFIG_FOLDER) : "INBOX");
            folder.open(Folder.READ_ONLY);
            int total = folder.getMessageCount();
            folder.close(false);
            long latency = System.currentTimeMillis() - started;
            return CapabilityTestResult.success("连接成功（" + total + " 封邮件，" + latency + "ms）");
        } catch (Exception e) {
            return CapabilityTestResult.failure("连接失败：" + e.getMessage());
        }
    }

    /** 运行侧闸门：管理后台启用后（且部署侧 Bean 存在）为 true。 */
    public boolean active() {
        return enabled.get();
    }

    /** 当前生效配置值；未配置返回空串。password 为运行态解密后的值，调用方不得外泄。 */
    public String configValue(String key) {
        return config.getOrDefault(key, "");
    }

    private Map<String, String> mergedConfig(Map<String, String> config) {
        Map<String, String> merged = new LinkedHashMap<>(descriptor().defaultConfig());
        if (config != null) {
            config.forEach((key, value) -> merged.put(key, value == null ? "" : value.trim()));
        }
        return merged;
    }

    private String env(String name, String fallback) {
        String value = environment == null ? null : environment.getProperty(name);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static int port(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
