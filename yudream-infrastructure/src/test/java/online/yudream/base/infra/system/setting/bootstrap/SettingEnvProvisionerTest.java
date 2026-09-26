package online.yudream.base.infra.system.setting.bootstrap;

import online.yudream.base.domain.system.integration.enumerate.IntegrationCategory;
import online.yudream.base.domain.system.integration.repo.SystemIntegrationConfigStore;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.infra.system.integration.SystemIntegrationSettings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingEnvProvisionerTest {

    private final InMemorySettingRepo repo = new InMemorySettingRepo();
    private final SystemIntegrationSettings settings = new SystemIntegrationSettings(
            emptyStore(), repo, null) {
        @Override
        public String effectiveWebUrl() {
            return "https://demo.example.com";
        }

        @Override
        public String effectiveBaseUrl() {
            return "https://api.demo.example.com";
        }
    };

    @Test
    void absentKeysAreProvisionedFromEnvironmentDerivedValues() {
        new SettingEnvProvisioner(repo, settings).onApplicationEvent(null);

        assertEquals("https://demo.example.com", repo.findByKey("app.web-url").orElseThrow().getValue());
        assertEquals("https://demo.example.com", repo.findByKey("APP_WEB_URL").orElseThrow().getValue());
        assertEquals("https://api.demo.example.com", repo.findByKey("app.base-url").orElseThrow().getValue());
        assertEquals("STRING", repo.findByKey("app.web-url").orElseThrow().getType().name());
        assertEquals("app", repo.findByKey("app.web-url").orElseThrow().getCategory());
    }

    @Test
    void staleValuesAreUpdatedToTheEnvironmentDerivedValue() {
        repo.save(Setting.builder().key("app.web-url").value("http://old.example.com")
                .type(online.yudream.base.domain.system.setting.enumerate.SettingType.STRING)
                .category("app").build());

        new SettingEnvProvisioner(repo, settings).onApplicationEvent(null);

        assertEquals("https://demo.example.com", repo.findByKey("app.web-url").orElseThrow().getValue());
    }

    @Test
    void alreadyAlignedKeysAreLeftUntouched() {
        Setting aligned = Setting.builder().key("app.web-url").value("https://demo.example.com")
                .type(online.yudream.base.domain.system.setting.enumerate.SettingType.STRING)
                .category("app").build();
        repo.save(aligned);
        Setting before = repo.findByKey("app.web-url").orElseThrow();

        new SettingEnvProvisioner(repo, settings).onApplicationEvent(null);

        // 已对齐键不得被重写（同一实例仍在库中即证明未重新保存）
        assertTrue(repo.findByKey("app.web-url").orElseThrow() == before);
        // 其余缺席键正常装载
        assertEquals("https://demo.example.com", repo.findByKey("APP_WEB_URL").orElseThrow().getValue());
    }

    private static SystemIntegrationConfigStore emptyStore() {
        return new SystemIntegrationConfigStore() {
            @Override
            public Map<String, String> load(IntegrationCategory category) {
                return Map.of();
            }

            @Override
            public void save(IntegrationCategory category, Map<String, String> values) {
            }
        };
    }

    private static class InMemorySettingRepo implements SettingRepo {

        private final Map<String, Setting> byKey = new HashMap<>();
        private final List<Setting> all = new ArrayList<>();
        private int saveCount;

        @Override
        public Setting save(Setting setting) {
            saveCount++;
            if (setting.getId() == null) {
                setting.setId((long) (all.size() + 1));
                setting.setCreateTime(java.time.LocalDateTime.now());
            }
            setting.setUpdateTime(java.time.LocalDateTime.now());
            byKey.put(setting.getKey(), setting);
            all.add(setting);
            return setting;
        }

        @Override
        public Optional<Setting> findByKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }

        @Override
        public boolean existsByKey(String key) {
            return byKey.containsKey(key);
        }

        @Override
        public List<Setting> findByCategory(String category) {
            return all.stream().filter(setting -> category.equals(setting.getCategory())).toList();
        }

        @Override
        public List<Setting> findAll() {
            return List.copyOf(all);
        }
    }
}
