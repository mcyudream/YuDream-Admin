package online.yudream.base.application.system.setting.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.setting.assembler.IntegrationConfigAssembler;
import online.yudream.base.application.system.setting.cmd.IntegrationConfigSaveCmd;
import online.yudream.base.application.system.setting.cmd.MailConfigCmd;
import online.yudream.base.application.system.setting.cmd.MailTestCmd;
import online.yudream.base.application.system.setting.cmd.StorageConfigCmd;
import online.yudream.base.application.system.setting.cmd.StorageTestCmd;
import online.yudream.base.application.system.setting.dto.IntegrationConfigDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.integration.enumerate.IntegrationCategory;
import online.yudream.base.domain.system.integration.repo.SystemIntegrationConfigStore;
import online.yudream.base.domain.system.integration.service.IntegrationProbe;
import online.yudream.base.domain.system.integration.valobj.IntegrationProbeResult;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统集成配置（邮件/对象存储）应用服务：入库值优先、环境变量兜底；
 * 密码/密钥加密存储、永不回显；测试用例对候选配置真实连通验证。
 */
@Service
@RequiredArgsConstructor
public class IntegrationConfigAppService {

    private static final String SOURCE_CUSTOM = "custom";
    private static final String SOURCE_ENVIRONMENT = "environment";
    private static final String SETUP_COMPLETED_KEY = "system.setup.completed";

    private final SystemIntegrationConfigStore store;
    private final SettingRepo settingRepo;
    private final IntegrationProbe probe;
    private final IntegrationConfigAssembler assembler;
    private final Environment environment;

    /** 部署级初始化令牌（可选）：与初始化接口共用同一把令牌。 */
    @Value("${YUDREAM_SETUP_TOKEN:}")
    private String setupTokenExpected;

    @Transactional(readOnly = true)
    public IntegrationConfigDTO get() {
        Map<String, String> mailSaved = store.load(IntegrationCategory.MAIL);
        Map<String, String> storageSaved = store.load(IntegrationCategory.STORAGE);
        IntegrationConfigDTO.MailSection mail = IntegrationConfigDTO.MailSection.builder()
                .host(value(mailSaved, "mail.host", envValue("spring.mail.host")))
                .port(intValue(mailSaved, "mail.port", envValue("spring.mail.port"), 465))
                .username(value(mailSaved, "mail.username", envValue("spring.mail.username")))
                .from(value(mailSaved, "mail.from", envValue("mail.from")))
                .ssl(boolValue(mailSaved, "mail.ssl", envValue("spring.mail.properties.mail.smtp.ssl.enable"), true))
                .starttls(boolValue(mailSaved, "mail.starttls", envValue("spring.mail.properties.mail.smtp.starttls.enable"), false))
                .passwordSet(hasAny(mailSaved.get("mail.password"), envValue("spring.mail.password")))
                .source(mailSaved.isEmpty() ? SOURCE_ENVIRONMENT : SOURCE_CUSTOM)
                .build();
        IntegrationConfigDTO.StorageSection storage = IntegrationConfigDTO.StorageSection.builder()
                .endpoint(value(storageSaved, "storage.endpoint", envValue("yudream.storage.s3.endpoint")))
                .accessKey(value(storageSaved, "storage.access-key", envValue("yudream.storage.s3.access-key")))
                .bucket(value(storageSaved, "storage.bucket", envValue("yudream.storage.s3.bucket")))
                .region(value(storageSaved, "storage.region", envValue("yudream.storage.s3.region"), "us-east-1"))
                .pathStyle(boolValue(storageSaved, "storage.path-style", envValue("yudream.storage.s3.path-style-access"), true))
                .secretKeySet(hasAny(storageSaved.get("storage.secret-key"), envValue("yudream.storage.s3.secret-key")))
                .source(storageSaved.isEmpty() ? SOURCE_ENVIRONMENT : SOURCE_CUSTOM)
                .build();
        return IntegrationConfigDTO.builder().mail(mail).storage(storage).build();
    }

    @Transactional
    public IntegrationConfigDTO update(IntegrationConfigSaveCmd cmd) {
        if (cmd.getMail() != null) {
            saveMail(cmd.getMail());
        }
        if (cmd.getStorage() != null) {
            saveStorage(cmd.getStorage());
        }
        return get();
    }

    public IntegrationProbeResult testMail(MailTestCmd cmd) {
        Map<String, String> saved = store.load(IntegrationCategory.MAIL);
        String effectivePassword = firstHasText(saved.get("mail.password"), envValue("spring.mail.password"));
        return probe.sendTestMail(assembler.toMailServerConfig(cmd, effectivePassword), cmd.getTo());
    }

    public IntegrationProbeResult testStorage(StorageTestCmd cmd) {
        Map<String, String> saved = store.load(IntegrationCategory.STORAGE);
        String effectiveSecret = firstHasText(saved.get("storage.secret-key"), envValue("yudream.storage.s3.secret-key"));
        return probe.checkStorage(assembler.toObjectStorageConfig(cmd, effectiveSecret),
                !Boolean.FALSE.equals(cmd.getAutoCreate()));
    }

    /** 安装窗口期守卫：初始化未完成才允许匿名调用；部署令牌可选校验。 */
    public void ensureSetupWindow(String setupToken) {
        boolean completed = settingRepo.findByKey(SETUP_COMPLETED_KEY)
                .map(setting -> Boolean.parseBoolean(setting.getValue()))
                .orElse(false);
        if (completed) {
            throw new BizException("系统已初始化，请在管理后台系统设置中修改集成配置");
        }
        if (StringUtils.hasText(setupTokenExpected)
                && !setupTokenExpected.equals(setupToken)) {
            throw new BizException("安装令牌缺失或不匹配");
        }
    }

    private void saveMail(MailConfigCmd mail) {
        if (!StringUtils.hasText(mail.getHost())) {
            throw new BizException("SMTP 主机不能为空");
        }
        Map<String, String> saved = store.load(IntegrationCategory.MAIL);
        Map<String, String> values = new HashMap<>(saved);
        values.put("mail.host", mail.getHost().trim());
        putPort(values, "mail.port", mail.getPort());
        putField(values, "mail.username", mail.getUsername());
        // 密码留空=保持已存值；显式传空串不生效，避免误清空导致不可用
        if (StringUtils.hasText(mail.getPassword())) {
            values.put("mail.password", mail.getPassword());
        }
        putField(values, "mail.from", mail.getFrom());
        putBoolean(values, "mail.ssl", mail.getSsl());
        putBoolean(values, "mail.starttls", mail.getStarttls());
        store.save(IntegrationCategory.MAIL, values);
    }

    private void saveStorage(StorageConfigCmd storage) {
        if (!StringUtils.hasText(storage.getEndpoint())) {
            throw new BizException("对象存储 Endpoint 不能为空");
        }
        Map<String, String> saved = store.load(IntegrationCategory.STORAGE);
        Map<String, String> values = new HashMap<>(saved);
        values.put("storage.endpoint", storage.getEndpoint().trim());
        putField(values, "storage.access-key", storage.getAccessKey());
        if (StringUtils.hasText(storage.getSecretKey())) {
            values.put("storage.secret-key", storage.getSecretKey());
        }
        putField(values, "storage.bucket", storage.getBucket());
        putField(values, "storage.region", storage.getRegion());
        putBoolean(values, "storage.path-style", storage.getPathStyle());
        store.save(IntegrationCategory.STORAGE, values);
    }

    private void putField(Map<String, String> values, String key, String value) {
        if (value == null) {
            return;
        }
        if (!StringUtils.hasText(value)) {
            values.put(key, "");
            return;
        }
        values.put(key, value.trim());
    }

    private void putPort(Map<String, String> values, String key, Integer port) {
        if (port != null) {
            if (port <= 0 || port > 65535) {
                throw new BizException("端口必须在 1-65535 之间");
            }
            values.put(key, String.valueOf(port));
        }
    }

    private void putBoolean(Map<String, String> values, String key, Boolean value) {
        if (value != null) {
            values.put(key, String.valueOf(value));
        }
    }

    private String value(Map<String, String> saved, String key, String... fallbacks) {
        String savedValue = saved.get(key);
        if (StringUtils.hasText(savedValue)) {
            return savedValue;
        }
        for (String fallback : fallbacks) {
            if (StringUtils.hasText(fallback)) {
                return fallback;
            }
        }
        return "";
    }

    private Integer intValue(Map<String, String> saved, String key, String envValue, int fallback) {
        String raw = firstHasText(saved.get(key), envValue);
        try {
            return StringUtils.hasText(raw) ? Integer.parseInt(raw.trim()) : fallback;
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    private Boolean boolValue(Map<String, String> saved, String key, String envValue, boolean fallback) {
        String raw = firstHasText(saved.get(key), envValue);
        return StringUtils.hasText(raw) ? Boolean.parseBoolean(raw.trim()) : fallback;
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

    private boolean hasAny(String... candidates) {
        return firstHasText(candidates) != null;
    }

    private String firstHasText(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
