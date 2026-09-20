package online.yudream.base.infra.system.integration.store;

import online.yudream.base.domain.system.integration.enumerate.IntegrationCategory;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingSystemIntegrationConfigStoreTest {

    private InMemorySettingRepo repo;
    private SettingSystemIntegrationConfigStore store;

    @BeforeEach
    void setUp() {
        repo = new InMemorySettingRepo();
        String key = Base64.getEncoder().encodeToString(new byte[]{
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31});
        store = new SettingSystemIntegrationConfigStore(repo, new CapabilityCredentialCipher(key));
    }

    @Test
    void secretValueIsStoredEncryptedAndLoadedDecrypted() {
        Map<String, String> values = new HashMap<>();
        values.put("mail.host", "smtp.example.com");
        values.put("mail.password", "super-secret");
        store.save(IntegrationCategory.MAIL, values);

        Setting storedPassword = repo.findByKey("mail.password").orElseThrow();
        assertNotEquals("super-secret", storedPassword.getValue());

        Map<String, String> loaded = store.load(IntegrationCategory.MAIL);
        assertEquals("smtp.example.com", loaded.get("mail.host"));
        assertEquals("super-secret", loaded.get("mail.password"));
    }

    @Test
    void blankValueIsTreatedAsUnsetOnLoad() {
        Map<String, String> values = new HashMap<>();
        values.put("mail.host", "smtp.example.com");
        values.put("mail.from", "");
        store.save(IntegrationCategory.MAIL, values);

        Map<String, String> loaded = store.load(IntegrationCategory.MAIL);
        assertTrue(loaded.containsKey("mail.host"));
        assertFalse(loaded.containsKey("mail.from"));
    }

    @Test
    void loadReturnsEmptyMapWhenNothingSaved() {
        assertTrue(store.load(IntegrationCategory.STORAGE).isEmpty());
    }

    @Test
    void updateOverwritesExistingKey() {
        store.save(IntegrationCategory.MAIL, Map.of("mail.host", "smtp-old.example.com"));
        store.save(IntegrationCategory.MAIL, Map.of("mail.host", "smtp-new.example.com"));
        assertEquals("smtp-new.example.com", store.load(IntegrationCategory.MAIL).get("mail.host"));
        assertEquals(1, repo.findByCategory(IntegrationCategory.MAIL.category()).size());
    }

    /** 仅保存行到内存，不依赖数据库。 */
    private static class InMemorySettingRepo implements SettingRepo {
        private final Map<String, Setting> byKey = new HashMap<>();

        @Override
        public Setting save(Setting setting) {
            byKey.put(setting.getKey(), setting);
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
            return byKey.values().stream()
                    .filter(setting -> category.equals(setting.getCategory()))
                    .toList();
        }

        @Override
        public List<Setting> findAll() {
            return List.copyOf(byKey.values());
        }
    }
}
