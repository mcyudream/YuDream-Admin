package online.yudream.base.bootstrap.application.system.setting.service;

import online.yudream.base.application.system.setting.assembler.IntegrationConfigAssembler;
import online.yudream.base.application.system.setting.cmd.IntegrationConfigSaveCmd;
import online.yudream.base.application.system.setting.cmd.MailConfigCmd;
import online.yudream.base.application.system.setting.cmd.MailTestCmd;
import online.yudream.base.application.system.setting.cmd.StorageConfigCmd;
import online.yudream.base.application.system.setting.dto.IntegrationConfigDTO;
import online.yudream.base.application.system.setting.service.IntegrationConfigAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.integration.enumerate.IntegrationCategory;
import online.yudream.base.domain.system.integration.repo.SystemIntegrationConfigStore;
import online.yudream.base.domain.system.integration.service.IntegrationProbe;
import online.yudream.base.domain.system.integration.valobj.IntegrationProbeResult;
import online.yudream.base.domain.system.integration.valobj.MailServerConfig;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationConfigAppServiceTest {

    private InMemoryStore store;
    private InMemorySettingRepo settingRepo;
    private StubProbe probe;
    private MockEnvironment environment;
    private IntegrationConfigAppService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        settingRepo = new InMemorySettingRepo();
        probe = new StubProbe();
        environment = new MockEnvironment();
        service = new IntegrationConfigAppService(store, settingRepo, probe,
                new IntegrationConfigAssembler(), environment);
    }

    @Test
    void getFallsBackToEnvironmentAndMarksSource() {
        environment.setProperty("spring.mail.host", "smtp.env.example.com");
        environment.setProperty("spring.mail.password", "env-secret");

        IntegrationConfigDTO dto = service.get();

        assertEquals("environment", dto.getMail().getSource());
        assertEquals("smtp.env.example.com", dto.getMail().getHost());
        assertTrue(dto.getMail().isPasswordSet());
        assertEquals("environment", dto.getStorage().getSource());
    }

    @Test
    void updateMergesAndKeepsExistingPasswordWhenBlank() {
        store.save(IntegrationCategory.MAIL, Map.of(
                "mail.host", "smtp-old.example.com",
                "mail.password", "old-secret"));

        IntegrationConfigSaveCmd cmd = new IntegrationConfigSaveCmd();
        MailConfigCmd mail = new MailConfigCmd();
        mail.setHost("smtp-new.example.com");
        mail.setPassword("");
        cmd.setMail(mail);
        service.update(cmd);

        Map<String, String> saved = store.load(IntegrationCategory.MAIL);
        assertEquals("smtp-new.example.com", saved.get("mail.host"));
        assertEquals("old-secret", saved.get("mail.password"));

        IntegrationConfigDTO dto = service.get();
        assertEquals("custom", dto.getMail().getSource());
        assertEquals("smtp-new.example.com", dto.getMail().getHost());
    }

    @Test
    void updateRejectsBlankMailHost() {
        IntegrationConfigSaveCmd cmd = new IntegrationConfigSaveCmd();
        MailConfigCmd mail = new MailConfigCmd();
        mail.setHost(" ");
        cmd.setMail(mail);
        assertThrows(BizException.class, () -> service.update(cmd));
    }

    @Test
    void updateRejectsBlankStorageEndpoint() {
        IntegrationConfigSaveCmd cmd = new IntegrationConfigSaveCmd();
        StorageConfigCmd storage = new StorageConfigCmd();
        storage.setEndpoint("");
        cmd.setStorage(storage);
        assertThrows(BizException.class, () -> service.update(cmd));
    }

    @Test
    void testMailFallsBackToStoredPassword() {
        store.save(IntegrationCategory.MAIL, Map.of("mail.password", "stored-secret"));
        MailTestCmd cmd = new MailTestCmd();
        cmd.setHost("smtp.example.com");
        cmd.setTo("someone@example.com");

        service.testMail(cmd);

        MailServerConfig config = probe.capturedMail.get();
        assertEquals("stored-secret", config.password());
        assertEquals("smtp.example.com", config.host());
    }

    @Test
    void ensureSetupWindowRejectsAfterCompleted() {
        settingRepo.save(Setting.builder()
                .key("system.setup.completed")
                .value("true")
                .build());
        assertThrows(BizException.class, () -> service.ensureSetupWindow(null));
    }

    @Test
    void ensureSetupWindowAllowsDuringSetup() {
        service.ensureSetupWindow(null);
    }

    private static class InMemoryStore implements SystemIntegrationConfigStore {
        private final Map<IntegrationCategory, Map<String, String>> data = new HashMap<>();

        @Override
        public Map<String, String> load(IntegrationCategory category) {
            return new LinkedHashMap<>(data.getOrDefault(category, Map.of()));
        }

        @Override
        public void save(IntegrationCategory category, Map<String, String> values) {
            data.put(category, new LinkedHashMap<>(values));
        }
    }

    private static class StubProbe implements IntegrationProbe {
        final AtomicReference<MailServerConfig> capturedMail = new AtomicReference<>();

        @Override
        public IntegrationProbeResult sendTestMail(MailServerConfig config, String to) {
            capturedMail.set(config);
            return IntegrationProbeResult.ok("测试邮件已发送");
        }

        @Override
        public IntegrationProbeResult checkStorage(online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig config, boolean autoCreate) {
            return IntegrationProbeResult.ok("Bucket 连接成功");
        }
    }

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
