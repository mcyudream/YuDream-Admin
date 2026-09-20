package online.yudream.base.infra.system.integration.store;

import online.yudream.base.domain.system.integration.enumerate.IntegrationCategory;
import online.yudream.base.domain.system.integration.repo.SystemIntegrationConfigStore;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统集成配置存储实现：值持久化到 sys_setting（分类见 {@link IntegrationCategory}），
 * secret 字段经能力凭据 AES-GCM 加密（AAD 绑定 system.integration.{分类}:{key}）。
 */
@Component
public class SettingSystemIntegrationConfigStore implements SystemIntegrationConfigStore {

    private static final String AAD_PREFIX = "system.integration.";

    private final SettingRepo settingRepo;
    private final CapabilityCredentialCipher credentialCipher;

    public SettingSystemIntegrationConfigStore(SettingRepo settingRepo,
                                               CapabilityCredentialCipher credentialCipher) {
        this.settingRepo = settingRepo;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public Map<String, String> load(IntegrationCategory category) {
        Map<String, String> values = new LinkedHashMap<>();
        List<Setting> settings = settingRepo.findByCategory(category.category());
        for (Setting setting : settings) {
            if (setting == null || !StringUtils.hasText(setting.getKey())
                    || !StringUtils.hasText(setting.getValue())) {
                continue;
            }
            values.put(setting.getKey(), decryptIfNeeded(category, setting.getKey(), setting.getValue()));
        }
        return values;
    }

    @Override
    public void save(IntegrationCategory category, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String raw = entry.getValue() == null ? "" : entry.getValue();
            boolean secret = category.isSecretKey(key);
            String stored = StringUtils.hasText(raw) && secret
                    ? credentialCipher.encryptSecret(aad(category), key, raw)
                    : raw;
            Setting existing = settingRepo.findByKey(key).orElse(null);
            if (existing != null) {
                existing.setValue(stored);
                settingRepo.save(existing);
                continue;
            }
            settingRepo.save(Setting.builder()
                    .key(key)
                    .value(stored)
                    .type(SettingType.STRING)
                    .category(category.category())
                    .description(descriptionOf(key))
                    .build());
        }
    }

    private String decryptIfNeeded(IntegrationCategory category, String key, String value) {
        if (!category.isSecretKey(key) || !credentialCipher.encrypted(value)) {
            return value;
        }
        return credentialCipher.decryptSecret(aad(category), key, value);
    }

    private String aad(IntegrationCategory category) {
        return AAD_PREFIX + category.category();
    }

    private String descriptionOf(String key) {
        return "系统集成配置：" + key;
    }
}
