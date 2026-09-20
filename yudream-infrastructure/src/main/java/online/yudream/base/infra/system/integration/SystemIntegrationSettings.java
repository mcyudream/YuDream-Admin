package online.yudream.base.infra.system.integration;

import online.yudream.base.domain.system.integration.enumerate.IntegrationCategory;
import online.yudream.base.domain.system.integration.repo.SystemIntegrationConfigStore;
import online.yudream.base.domain.system.integration.valobj.MailServerConfig;
import online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 系统集成有效配置解析器：数据库保存值优先，环境变量/application.yml 兜底；
 * 同时提供站点地址的有效值解析与「来源」标记（custom=入库 / environment=环境兜底）。
 */
@Component
public class SystemIntegrationSettings {

    public static final String SETUP_COMPLETED_KEY = "system.setup.completed";

    private final SystemIntegrationConfigStore store;
    private final SettingRepo settingRepo;
    private final Environment environment;

    public SystemIntegrationSettings(SystemIntegrationConfigStore store,
                                     SettingRepo settingRepo,
                                     Environment environment) {
        this.store = store;
        this.settingRepo = settingRepo;
        this.environment = environment;
    }

    /** 是否保存过邮件自定义配置。 */
    public boolean mailCustomized() {
        return !store.load(IntegrationCategory.MAIL).isEmpty();
    }

    /** 是否保存过对象存储自定义配置。 */
    public boolean storageCustomized() {
        return !store.load(IntegrationCategory.STORAGE).isEmpty();
    }

    /**
     * 有效邮件配置：入库值优先，回落 spring.mail.* / MAIL_FROM。
     */
    public MailServerConfig effectiveMail() {
        Map<String, String> saved = store.load(IntegrationCategory.MAIL);
        String host = first(saved.get("mail.host"), envValue("spring.mail.host"));
        int port = Integer.parseInt(first(saved.get("mail.port"), envValue("spring.mail.port"), "465"));
        String username = first(saved.get("mail.username"), envValue("spring.mail.username"));
        String password = first(saved.get("mail.password"), envValue("spring.mail.password"));
        String from = first(saved.get("mail.from"), envValue("mail.from"), username);
        boolean ssl = Boolean.parseBoolean(first(saved.get("mail.ssl"),
                envValue("spring.mail.properties.mail.smtp.ssl.enable"), "true"));
        boolean starttls = Boolean.parseBoolean(first(saved.get("mail.starttls"),
                envValue("spring.mail.properties.mail.smtp.starttls.enable"), "false"));
        return new MailServerConfig(host, port, username, password, from, ssl, starttls);
    }

    /**
     * 有效对象存储配置：入库值优先，回落 yudream.storage.s3.*。
     */
    public ObjectStorageConfig effectiveStorage() {
        Map<String, String> saved = store.load(IntegrationCategory.STORAGE);
        String endpoint = first(saved.get("storage.endpoint"), envValue("yudream.storage.s3.endpoint"));
        String accessKey = first(saved.get("storage.access-key"), envValue("yudream.storage.s3.access-key"));
        String secretKey = first(saved.get("storage.secret-key"), envValue("yudream.storage.s3.secret-key"));
        String bucket = first(saved.get("storage.bucket"), envValue("yudream.storage.s3.bucket"));
        String region = first(saved.get("storage.region"), envValue("yudream.storage.s3.region"), "us-east-1");
        boolean pathStyle = Boolean.parseBoolean(first(saved.get("storage.path-style"),
                envValue("yudream.storage.s3.path-style-access"), "true"));
        return new ObjectStorageConfig(endpoint, accessKey, secretKey, bucket, region, pathStyle);
    }

    /**
     * 站点地址有效值：入库 site.web-url 优先，回落 app.web-url → app.base-url。
     */
    public String effectiveWebUrl() {
        String saved = savedValue("site.web-url");
        String resolved = first(saved, envValue("app.web-url"),
                envValue("app.base-url"), "http://localhost:9000");
        return trimTrailingSlash(resolved);
    }

    /**
     * 站点接口地址有效值：入库 site.base-url 优先，回落 app.base-url。
     */
    public String effectiveBaseUrl() {
        String saved = savedValue("site.base-url");
        String resolved = first(saved, envValue("app.base-url"), "http://localhost:8080");
        return trimTrailingSlash(resolved);
    }

    /** 初始化是否已完成（安装窗口期判断）。 */
    public boolean setupCompleted() {
        return settingRepo.findByKey(SETUP_COMPLETED_KEY)
                .map(setting -> Boolean.parseBoolean(setting.getValue()))
                .orElse(false);
    }

    /** 安全读取环境/yml 配置：application.yml 中未解析的占位符（如 ${MAIL_USERNAME} 无默认值）按未配置处理。 */
    private String envValue(String key) {
        try {
            return environment.getProperty(key);
        }
        catch (Exception e) {
            return null;
        }
    }

    private String savedValue(String key) {
        return settingRepo.findByKey(key)
                .map(Setting::getValue)
                .filter(StringUtils::hasText)
                .orElse(null);
    }

    private String first(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return "";
    }

    private String trimTrailingSlash(String url) {
        String trimmed = url == null ? "" : url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
