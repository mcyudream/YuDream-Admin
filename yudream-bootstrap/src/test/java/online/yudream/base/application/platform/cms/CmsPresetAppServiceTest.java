package online.yudream.base.application.platform.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.cms.cmd.HomePagePresetSaveCmd;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;
import online.yudream.base.application.platform.cms.service.CmsPresetAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.aggregate.HomePagePreset;
import online.yudream.base.domain.platform.cms.enumerate.HomePagePresetSource;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import online.yudream.base.domain.platform.cms.repo.HomePagePresetRepo;
import online.yudream.base.domain.shared.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsPresetAppServiceTest {

    private InMemoryCapabilityModuleRepo capabilityModuleRepo;
    private InMemoryHomePageLayoutRepo layoutRepo;
    private InMemoryHomePagePresetRepo presetRepo;
    private CmsPresetAppService service;

    @BeforeEach
    void setUp() {
        capabilityModuleRepo = new InMemoryCapabilityModuleRepo();
        layoutRepo = new InMemoryHomePageLayoutRepo();
        presetRepo = new InMemoryHomePagePresetRepo();
        AtomicLong sequence = new AtomicLong(1000);
        IdGenerator idGenerator = sequence::incrementAndGet;
        service = new CmsPresetAppService(capabilityModuleRepo, layoutRepo, presetRepo, idGenerator, new ObjectMapper());
    }

    @Test
    void saveCurrentAsPresetSnapshotsCurrentLayout() {
        layoutRepo.store(layout("原首页", Map.of("homeCss", "body{}"), true));

        HomePagePresetDTO saved = service.saveCurrentAsPreset(cmd("我的方案", "备份"));

        assertThat(saved.getCode()).startsWith("user-");
        assertThat(saved.getSource()).isEqualTo("USER");
        HomePagePreset preset = presetRepo.findByCode(saved.getCode()).orElseThrow();
        assertThat(preset.getTitle()).isEqualTo("原首页");
        assertThat(preset.getSettings()).containsEntry("homeCss", "body{}");
    }

    @Test
    void saveCurrentAsPresetRequiresName() {
        assertThatThrownBy(() -> service.saveCurrentAsPreset(cmd(" ", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("方案名称不能为空");
    }

    @Test
    void applyAutoSnapshotsCurrentAndRewritesLayoutKeepingPublished() {
        layoutRepo.store(layout("旧首页", Map.of("navigationJson", "[]"), true));
        HomePagePreset preset = presetRepo.save(HomePagePreset.builder()
                .code("user-1").name("新方案").source(HomePagePresetSource.USER)
                .title("新首页").subtitle("副标题").heroImageUrl("hero.png")
                .settings(Map.of("homeCss", ".pixel{}"))
                .sections(List.of())
                .build());

        service.apply("user-1");

        HomePageLayout current = layoutRepo.findCurrent().orElseThrow();
        assertThat(current.getTitle()).isEqualTo("新首页");
        assertThat(current.getTheme()).isEqualTo("user-1");
        assertThat(current.getSettings()).containsEntry("homeCss", ".pixel{}");
        assertThat(current.getPublished()).isTrue();
        List<HomePagePreset> snapshots = presetRepo.findBySource(HomePagePresetSource.SNAPSHOT);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().getTitle()).isEqualTo("旧首页");
        assertThat(snapshots.getFirst().getSettings()).containsEntry("navigationJson", "[]");
        assertThat(preset.getCode()).isEqualTo("user-1");
    }

    @Test
    void applyUnknownPresetFails() {
        assertThatThrownBy(() -> service.apply("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("方案不存在");
    }

    @Test
    void repeatedApplyOfSameContentSkipsDuplicateSnapshot() {
        layoutRepo.store(layout("默认", Map.of(), false));
        presetRepo.save(HomePagePreset.builder()
                .code("user-1").name("A").source(HomePagePresetSource.USER)
                .title("A 首页").settings(Map.of()).sections(List.of())
                .build());

        service.apply("user-1");
        service.apply("user-1");
        service.apply("user-1");

        assertThat(presetRepo.findBySource(HomePagePresetSource.SNAPSHOT)).hasSize(2);
    }

    @Test
    void snapshotsAreCappedAtTen() {
        layoutRepo.store(layout("默认", Map.of(), false));
        presetRepo.save(HomePagePreset.builder()
                .code("user-a").name("A").source(HomePagePresetSource.USER)
                .title("A").settings(Map.of()).sections(List.of()).build());
        presetRepo.save(HomePagePreset.builder()
                .code("user-b").name("B").source(HomePagePresetSource.USER)
                .title("B").settings(Map.of()).sections(List.of()).build());

        for (int i = 0; i < 12; i++) {
            service.apply(i % 2 == 0 ? "user-a" : "user-b");
        }

        assertThat(presetRepo.findBySource(HomePagePresetSource.SNAPSHOT)).hasSize(10);
    }

    @Test
    void deleteRemovesPresetAndFailsOnUnknown() {
        presetRepo.save(HomePagePreset.builder()
                .code("user-1").name("A").source(HomePagePresetSource.USER)
                .settings(Map.of()).sections(List.of()).build());

        service.delete("user-1");

        assertThat(presetRepo.findByCode("user-1")).isEmpty();
        assertThatThrownBy(() -> service.delete("user-1"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void capabilityDisabledRejectsUserOperationsButSkipsPluginImport() {
        capabilityModuleRepo.enabled = false;

        assertThatThrownBy(() -> service.list()).isInstanceOf(BizException.class)
                .hasMessageContaining("内容定制能力未启用");
        assertThatThrownBy(() -> service.apply("user-1")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.saveCurrentAsPreset(cmd("x", null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.delete("user-1")).isInstanceOf(BizException.class);
        assertThat(service.importPluginPreset("neco", "Neco", "{\"title\":\"x\"}")).isFalse();
        assertThat(presetRepo.findAll()).isEmpty();
    }

    @Test
    void importPluginPresetMergesDeclaredSettingsAndKeepsNavigation() {
        layoutRepo.store(layout("旧首页", new HashMap<>(Map.of(
                "navigationJson", "[{\"name\":\"wiki\"}]", "homeCss", "old")), true));

        boolean imported = service.importPluginPreset("neco", "Neco 主题", """
                {"title":"像素首页","subtitle":"像素风","settings":{"homeCss":".pixel{}"}}
                """);

        assertThat(imported).isTrue();
        HomePagePreset preset = presetRepo.findByCode("plugin:neco").orElseThrow();
        assertThat(preset.getSource()).isEqualTo(HomePagePresetSource.PLUGIN);
        assertThat(preset.getSettings())
                .containsEntry("homeCss", ".pixel{}")
                .containsEntry("navigationJson", "[{\"name\":\"wiki\"}]");
        HomePageLayout current = layoutRepo.findCurrent().orElseThrow();
        assertThat(current.getTitle()).isEqualTo("像素首页");
        assertThat(current.getTheme()).isEqualTo("plugin:neco");
        assertThat(current.getPublished()).isTrue();
        assertThat(presetRepo.findBySource(HomePagePresetSource.SNAPSHOT)).hasSize(1);
    }

    @Test
    void importPluginPresetUpsertsSamePluginAndPreservesIdentity() {
        layoutRepo.store(layout("旧首页", Map.of(), false));
        service.importPluginPreset("neco", "Neco", "{\"title\":\"v1\"}");
        HomePagePreset first = presetRepo.findByCode("plugin:neco").orElseThrow();

        service.importPluginPreset("neco", "Neco", "{\"title\":\"v2\"}");

        HomePagePreset second = presetRepo.findByCode("plugin:neco").orElseThrow();
        assertThat(second.getTitle()).isEqualTo("v2");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getCreateTime()).isEqualTo(first.getCreateTime());
    }

    @Test
    void importPluginPresetRejectsEmptyOrContentlessPayload() {
        layoutRepo.store(layout("旧首页", Map.of(), false));

        assertThatThrownBy(() -> service.importPluginPreset("neco", "Neco", " "))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.importPluginPreset("neco", "Neco", "{}"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.importPluginPreset("neco", "Neco", "not-json"))
                .isInstanceOf(BizException.class);
    }

    private HomePagePresetSaveCmd cmd(String name, String description) {
        HomePagePresetSaveCmd cmd = new HomePagePresetSaveCmd();
        cmd.setName(name);
        cmd.setDescription(description);
        return cmd;
    }

    private HomePageLayout layout(String title, Map<String, String> settings, boolean published) {
        return HomePageLayout.builder()
                .id(1L)
                .title(title)
                .subtitle("副标题")
                .theme("default")
                .settings(new HashMap<>(settings))
                .sections(new ArrayList<>())
                .published(published)
                .build();
    }

    static class InMemoryCapabilityModuleRepo implements CapabilityModuleRepo {

        private boolean enabled = true;

        @Override
        public CapabilityModule save(CapabilityModule module) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CapabilityModule> findByCode(String code) {
            return Optional.of(CapabilityModule.builder().code(code).enabled(enabled).build());
        }

        @Override
        public List<CapabilityModule> findAll() {
            return List.of();
        }
    }

    static class InMemoryHomePageLayoutRepo implements HomePageLayoutRepo {

        private HomePageLayout current;

        void store(HomePageLayout layout) {
            this.current = layout;
        }

        @Override
        public HomePageLayout save(HomePageLayout layout) {
            this.current = layout;
            return layout;
        }

        @Override
        public Optional<HomePageLayout> findCurrent() {
            return Optional.ofNullable(current);
        }
    }

    static class InMemoryHomePagePresetRepo implements HomePagePresetRepo {

        private final Map<String, HomePagePreset> store = new LinkedHashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public HomePagePreset save(HomePagePreset preset) {
            if (preset.getId() == null) {
                preset.setId(idSequence.getAndIncrement());
            }
            if (preset.getCreateTime() == null) {
                preset.setCreateTime(LocalDateTime.now());
            }
            preset.setUpdateTime(LocalDateTime.now());
            store.put(preset.getCode(), preset);
            return preset;
        }

        @Override
        public Optional<HomePagePreset> findByCode(String code) {
            return Optional.ofNullable(store.get(code));
        }

        @Override
        public List<HomePagePreset> findAll() {
            return sorted(store.values().stream().toList());
        }

        @Override
        public List<HomePagePreset> findBySource(HomePagePresetSource source) {
            return sorted(store.values().stream().filter(preset -> preset.getSource() == source).toList());
        }

        @Override
        public void deleteByCode(String code) {
            store.remove(code);
        }

        private List<HomePagePreset> sorted(List<HomePagePreset> presets) {
            return presets.stream()
                    .sorted(Comparator.comparing(HomePagePreset::getCreateTime,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .toList();
        }
    }
}
