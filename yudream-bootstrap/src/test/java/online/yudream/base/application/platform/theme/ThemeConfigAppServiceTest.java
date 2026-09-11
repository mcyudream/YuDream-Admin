package online.yudream.base.application.platform.theme;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.theme.cmd.ThemeConfigSaveCmd;
import online.yudream.base.application.platform.theme.dto.ThemeConfigDTO;
import online.yudream.base.application.platform.theme.service.ThemeConfigAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendAssetInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginThemeInfo;
import online.yudream.base.domain.platform.theme.service.ThemeConfigSecretCipher;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThemeConfigAppServiceTest {

    private static final String SCHEMA_JSON = """
            {"sections":[
              {"code":"hero","title":"首屏","fields":[
                {"key":"heroTitle","label":"主标题","type":"text","default":"南京大学Minecraft协会"},
                {"key":"showServers","label":"显示服务器","type":"switch","default":true},
                {"key":"newsLimit","label":"新闻数量","type":"number","default":6},
                {"key":"accent","label":"强调色","type":"select","default":"green",
                 "options":[{"label":"绿","value":"green"},{"label":"蓝","value":"blue"}]},
                {"key":"heroImage","label":"背景图","type":"image","default":"/api/plugins/bg.webp"},
                {"key":"apiToken","label":"API 令牌","type":"text","secret":true}
              ]},
              {"code":"about","title":"关于","fields":[
                {"key":"introItems","label":"介绍项","type":"list","itemFields":[
                  {"key":"title","label":"标题","type":"text"},
                  {"key":"weight","label":"权重","type":"number"}
                ]}
              ]}
            ]}
            """;

    @Mock
    private PluginRuntimeGateway pluginRuntimeGateway;

    private InMemorySettingRepo settingRepo;
    private ThemeConfigAppService service;

    @BeforeEach
    void setUp() {
        settingRepo = new InMemorySettingRepo();
        service = new ThemeConfigAppService(settingRepo, pluginRuntimeGateway, new FakeCipher(), new ObjectMapper());
    }

    @Test
    void defaultThemeHasNoConfigSchema() {
        ThemeConfigDTO config = service.config("default");

        assertThat(config.getSchema()).isNull();
        assertThat(config.getValues()).isEmpty();
        assertThat(service.publicConfig("default")).isEmpty();
    }

    @Test
    void configMergesSchemaDefaultsAndMasksSecrets() {
        stubTheme();

        ThemeConfigDTO config = service.config("neco");

        assertThat(config.getSchema().getSections()).hasSize(2);
        assertThat(config.getValues())
                .containsEntry("heroTitle", "南京大学Minecraft协会")
                .containsEntry("showServers", true)
                .containsEntry("newsLimit", 6)
                .containsEntry("heroImage", "/api/plugins/bg.webp")
                .containsEntry("apiToken", "");
        assertThat(config.getSecretConfigured()).containsEntry("apiToken", false);
    }

    @Test
    void saveCoercesTypesAndPersistsJson() {
        stubTheme();
        ThemeConfigSaveCmd cmd = cmd("neco", Map.of(
                "heroTitle", "南大 MC",
                "newsLimit", "8",
                "showServers", "false",
                "unknownKey", "忽略我",
                "introItems", List.of(Map.of("title", "招新", "weight", "2"))));

        ThemeConfigDTO config = service.save(cmd);


        assertThat(config.getValues())
                .containsEntry("heroTitle", "南大 MC")
                .containsEntry("newsLimit", 8)
                .containsEntry("showServers", false);
        assertThat(config.getValues().get("introItems"))
                .isEqualTo(List.of(Map.of("title", "招新", "weight", 2)));
        Setting stored = settingRepo.findByKey("pluginTheme.config.neco").orElseThrow();
        assertThat(stored.getCategory()).isEqualTo("plugin-theme");
        assertThat(stored.getValue()).contains("\"newsLimit\":8").doesNotContain("unknownKey");
    }

    @Test
    void secretEncryptedMaskedAndStrippedFromPublicConfig() {
        stubTheme();
        service.save(cmd("neco", Map.of("apiToken", "s3cret", "heroTitle", "南大 MC")));

        Setting stored = settingRepo.findByKey("pluginTheme.config.neco").orElseThrow();
        assertThat(stored.getValue()).contains("enc:apiToken:s3cret").doesNotContain("\"apiToken\":\"s3cret\"");
        ThemeConfigDTO config = service.config("neco");
        assertThat(config.getValues()).containsEntry("apiToken", "");
        assertThat(config.getSecretConfigured()).containsEntry("apiToken", true);
        assertThat(service.publicConfig("neco"))
                .containsEntry("heroTitle", "南大 MC")
                .doesNotContainKey("apiToken");

        // 敏感字段留空保存 = 保持不变
        service.save(cmd("neco", Map.of("apiToken", "")));
        assertThat(settingRepo.findByKey("pluginTheme.config.neco").orElseThrow().getValue())
                .contains("enc:apiToken:s3cret");
    }

    @Test
    void saveEmptyImageKeepsClearedValueInsteadOfSchemaDefault() {
        stubTheme();
        service.save(cmd("neco", Map.of("heroImage", "")));

        assertThat(service.config("neco").getValues()).containsEntry("heroImage", "");
        assertThat(service.publicConfig("neco")).containsEntry("heroImage", "");
        assertThat(settingRepo.findByKey("pluginTheme.config.neco").orElseThrow().getValue())
                .contains("\"heroImage\":\"\"");
    }

    @Test
    void saveRejectsThemeWithoutSchema() {
        PluginThemeInfo theme = new PluginThemeInfo("plain", "plain", "朴素主题", "",
                Set.of("SITE"), List.of("theme.css"), "", "", "", "", "", "", "rev-1");
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(theme));

        assertThatThrownBy(() -> service.save(cmd("plain", Map.of("a", "b"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未声明配置项");
    }

    @Test
    void saveRejectsValueOutsideSelectOptions() {
        stubTheme();

        assertThatThrownBy(() -> service.save(cmd("neco", Map.of("accent", "red"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("可选项");
    }

    @Test
    void publicConfigDegradesToEmptyWhenSchemaAssetBroken() {
        PluginThemeInfo theme = theme("neco");
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(theme));
        when(pluginRuntimeGateway.frontendAsset("neco", "theme-config.json"))
                .thenReturn(Optional.of(new PluginFrontendAssetInfo("theme-config.json", "application/json",
                        "{not-json".getBytes(StandardCharsets.UTF_8))));

        assertThat(service.publicConfig("neco")).isEmpty();
    }

    private void stubTheme() {
        PluginThemeInfo theme = theme("neco");
        when(pluginRuntimeGateway.themes()).thenReturn(List.of(theme));
        lenient().when(pluginRuntimeGateway.frontendAsset("neco", "theme-config.json"))
                .thenReturn(Optional.of(new PluginFrontendAssetInfo("theme-config.json", "application/json",
                        SCHEMA_JSON.getBytes(StandardCharsets.UTF_8))));
    }

    private PluginThemeInfo theme(String code) {
        return new PluginThemeInfo(code, code, "Neco 像素", "",
                Set.of("SITE"), List.of("theme/neco.css"), "", "", "theme-config.json", "", "", "", "rev-1");
    }

    private ThemeConfigSaveCmd cmd(String themeCode, Map<String, Object> values) {
        ThemeConfigSaveCmd cmd = new ThemeConfigSaveCmd();
        cmd.setThemeCode(themeCode);
        cmd.setValues(values);
        return cmd;
    }

    private static class FakeCipher implements ThemeConfigSecretCipher {
        @Override
        public boolean canEncrypt() {
            return true;
        }

        @Override
        public boolean encrypted(String value) {
            return value != null && value.startsWith("enc:");
        }

        @Override
        public String encrypt(String themeCode, String fieldKey, String plaintext) {
            return "enc:" + fieldKey + ":" + plaintext;
        }
    }

    private static class InMemorySettingRepo implements SettingRepo {
        private final Map<String, Setting> store = new LinkedHashMap<>();

        @Override
        public Setting save(Setting setting) {
            store.put(setting.getKey(), setting);
            return setting;
        }

        @Override
        public Optional<Setting> findByKey(String key) {
            return Optional.ofNullable(store.get(key));
        }

        @Override
        public boolean existsByKey(String key) {
            return store.containsKey(key);
        }

        @Override
        public List<Setting> findByCategory(String category) {
            List<Setting> result = new ArrayList<>();
            for (Setting setting : store.values()) {
                if (category.equals(setting.getCategory())) {
                    result.add(setting);
                }
            }
            return result;
        }

        @Override
        public List<Setting> findAll() {
            return new ArrayList<>(store.values());
        }
    }
}
