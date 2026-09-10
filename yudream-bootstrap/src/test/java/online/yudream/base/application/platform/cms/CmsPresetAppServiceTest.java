package online.yudream.base.application.platform.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.cms.cmd.HomePagePresetSaveCmd;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;
import online.yudream.base.application.platform.cms.service.CmsPresetAppService;
import online.yudream.base.application.platform.theme.service.SiteThemeQueryService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.aggregate.HomePagePreset;
import online.yudream.base.domain.platform.cms.enumerate.HomePagePresetSource;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import online.yudream.base.domain.platform.cms.repo.HomePagePresetRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
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

    private static final String DEFAULT = HomePageLayout.DEFAULT_THEME_CODE;

    private InMemoryCapabilityModuleRepo capabilityModuleRepo;
    private InMemoryHomePageLayoutRepo layoutRepo;
    private InMemoryHomePagePresetRepo presetRepo;
    private InMemoryCmsPageRepo pageRepo;
    private InMemorySettingRepo settingRepo;
    private CmsPresetAppService service;

    @BeforeEach
    void setUp() {
        capabilityModuleRepo = new InMemoryCapabilityModuleRepo();
        layoutRepo = new InMemoryHomePageLayoutRepo();
        presetRepo = new InMemoryHomePagePresetRepo();
        pageRepo = new InMemoryCmsPageRepo();
        settingRepo = new InMemorySettingRepo();
        AtomicLong sequence = new AtomicLong(1000);
        IdGenerator idGenerator = sequence::incrementAndGet;
        service = new CmsPresetAppService(capabilityModuleRepo, layoutRepo, presetRepo, pageRepo,
                idGenerator, new ObjectMapper(), new SiteThemeQueryService(settingRepo));
    }

    @Test
    void saveCurrentAsPresetSnapshotsTargetThemeLayout() {
        layoutRepo.store(layout(DEFAULT, "原首页", Map.of("homeCss", "body{}"), true));

        HomePagePresetDTO saved = service.saveCurrentAsPreset(null, cmd("我的方案", "备份"));

        assertThat(saved.getCode()).startsWith("user-");
        assertThat(saved.getSource()).isEqualTo("USER");
        assertThat(saved.getThemeCode()).isEqualTo(DEFAULT);
        HomePagePreset preset = presetRepo.findByCode(saved.getCode()).orElseThrow();
        assertThat(preset.getThemeCode()).isEqualTo(DEFAULT);
        assertThat(preset.getTitle()).isEqualTo("原首页");
        assertThat(preset.getSettings()).containsEntry("homeCss", "body{}");
    }

    @Test
    void saveCurrentAsPresetRequiresName() {
        assertThatThrownBy(() -> service.saveCurrentAsPreset(null, cmd(" ", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("方案名称不能为空");
    }

    @Test
    void applyWritesPresetIntoItsOwnThemeLayoutAndKeepsPublished() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of("navigationJson", "[]"), true));
        layoutRepo.store(layout("neco", "旧首页", Map.of("homeCss", "old"), true));
        presetRepo.save(preset("user-1", "neco", HomePagePresetSource.USER)
                .title("新首页").subtitle("副标题").heroImageUrl("hero.png")
                .settings(Map.of("homeCss", ".pixel{}"))
                .sections(List.of())
                .build());

        service.apply("user-1");

        HomePageLayout necoLayout = layoutRepo.findByThemeCode("neco").orElseThrow();
        assertThat(necoLayout.getTitle()).isEqualTo("新首页");
        assertThat(necoLayout.getTheme()).isEqualTo("user-1");
        assertThat(necoLayout.getSettings()).containsEntry("homeCss", ".pixel{}");
        assertThat(necoLayout.getPublished()).isTrue();
        HomePageLayout defaultLayout = layoutRepo.findByThemeCode(DEFAULT).orElseThrow();
        assertThat(defaultLayout.getTitle()).isEqualTo("默认首页");
        assertThat(defaultLayout.getSettings()).containsEntry("navigationJson", "[]");
        List<HomePagePreset> snapshots = presetRepo.findBySource(HomePagePresetSource.SNAPSHOT);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().getThemeCode()).isEqualTo("neco");
        assertThat(snapshots.getFirst().getTitle()).isEqualTo("旧首页");
        assertThat(snapshots.getFirst().getSettings()).containsEntry("homeCss", "old");
    }

    @Test
    void applyUnknownPresetFails() {
        assertThatThrownBy(() -> service.apply("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("方案不存在");
    }

    @Test
    void repeatedApplyOfSameContentSkipsDuplicateSnapshot() {
        layoutRepo.store(layout(DEFAULT, "默认", Map.of(), false));
        presetRepo.save(preset("user-1", DEFAULT, HomePagePresetSource.USER)
                .name("A").title("A 首页").settings(Map.of()).sections(List.of()).build());

        service.apply("user-1");
        service.apply("user-1");
        service.apply("user-1");

        assertThat(presetRepo.findBySource(HomePagePresetSource.SNAPSHOT)).hasSize(2);
    }

    @Test
    void snapshotsAreCappedAtTenPerTheme() {
        layoutRepo.store(layout(DEFAULT, "默认", Map.of(), false));
        layoutRepo.store(layout("neco", "像素", Map.of(), false));
        presetRepo.save(preset("user-a", DEFAULT, HomePagePresetSource.USER)
                .name("A").title("A").settings(Map.of()).sections(List.of()).build());
        presetRepo.save(preset("user-b", DEFAULT, HomePagePresetSource.USER)
                .name("B").title("B").settings(Map.of()).sections(List.of()).build());
        presetRepo.save(HomePagePreset.snapshotOf("snapshot-keep", "别主题快照", null,
                HomePagePresetSource.SNAPSHOT, null, layoutRepo.findByThemeCode("neco").orElseThrow()));

        for (int i = 0; i < 12; i++) {
            service.apply(i % 2 == 0 ? "user-a" : "user-b");
        }

        List<HomePagePreset> snapshots = presetRepo.findBySource(HomePagePresetSource.SNAPSHOT);
        assertThat(snapshots.stream().filter(s -> DEFAULT.equals(s.getThemeCode()))).hasSize(10);
        assertThat(snapshots.stream().filter(s -> "neco".equals(s.getThemeCode())))
                .extracting(HomePagePreset::getCode).containsExactly("snapshot-keep");
    }

    @Test
    void deleteRemovesPresetAndFailsOnUnknown() {
        presetRepo.save(preset("user-1", DEFAULT, HomePagePresetSource.USER)
                .name("A").settings(Map.of()).sections(List.of()).build());

        service.delete("user-1");

        assertThat(presetRepo.findByCode("user-1")).isEmpty();
        assertThatThrownBy(() -> service.delete("user-1"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void capabilityDisabledRejectsUserOperationsButSkipsPluginImport() {
        capabilityModuleRepo.enabled = false;

        assertThatThrownBy(() -> service.list(null)).isInstanceOf(BizException.class)
                .hasMessageContaining("内容定制能力未启用");
        assertThatThrownBy(() -> service.apply("user-1")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.saveCurrentAsPreset(null, cmd("x", null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.delete("user-1")).isInstanceOf(BizException.class);
        assertThat(service.importPluginPreset("neco", "Neco", "{\"title\":\"x\"}")).isFalse();
        assertThat(presetRepo.findAll()).isEmpty();
    }

    @Test
    void capabilityDisabledSkipsThemeLayoutInitialization() {
        capabilityModuleRepo.enabled = false;
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), true));

        service.ensureThemeLayout("neco");

        assertThat(layoutRepo.findByThemeCode("neco")).isEmpty();
    }

    @Test
    void listScopesPresetsByThemeAndMarksActiveFromThatThemesLayout() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), true));
        layoutRepo.store(layout("neco", "像素首页", Map.of(), true));
        presetRepo.save(preset("user-1", DEFAULT, HomePagePresetSource.USER)
                .name("默认方案").settings(Map.of()).sections(List.of()).build());
        presetRepo.save(preset("user-2", "neco", HomePagePresetSource.USER)
                .name("像素方案").settings(Map.of()).sections(List.of()).build());
        HomePageLayout necoLayout = layoutRepo.findByThemeCode("neco").orElseThrow();
        necoLayout.setTheme("user-2");

        List<HomePagePresetDTO> necoPresets = service.list("neco");

        assertThat(necoPresets).extracting(HomePagePresetDTO::getCode).containsExactly("user-2");
        assertThat(necoPresets.getFirst().getActive()).isTrue();
        assertThat(service.list(DEFAULT)).extracting(HomePagePresetDTO::getCode).containsExactly("user-1");
        assertThat(service.list(DEFAULT).getFirst().getActive()).isFalse();
    }

    @Test
    void listDefaultsToActiveSiteTheme() {
        layoutRepo.store(layout("neco", "像素首页", Map.of(), true));
        presetRepo.save(preset("user-2", "neco", HomePagePresetSource.USER)
                .name("像素方案").settings(Map.of()).sections(List.of()).build());
        presetRepo.save(preset("user-1", DEFAULT, HomePagePresetSource.USER)
                .name("默认方案").settings(Map.of()).sections(List.of()).build());
        settingRepo.setActiveSite("neco");

        List<HomePagePresetDTO> presets = service.list(null);

        assertThat(presets).extracting(HomePagePresetDTO::getCode).containsExactly("user-2");
    }

    @Test
    void ensureThemeLayoutClonesDefaultThemeLayoutOnce() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of("navigationJson", "[]"), true));

        service.ensureThemeLayout("pixel");

        HomePageLayout clone = layoutRepo.findByThemeCode("pixel").orElseThrow();
        assertThat(clone.getTitle()).isEqualTo("默认首页");
        assertThat(clone.getSettings()).containsEntry("navigationJson", "[]");
        assertThat(clone.getPublished()).isTrue();

        layoutRepo.save(layout("pixel", "已被改", Map.of(), false));
        service.ensureThemeLayout("pixel");
        assertThat(layoutRepo.findByThemeCode("pixel").orElseThrow().getTitle()).isEqualTo("已被改");
    }

    @Test
    void importPluginPresetCreatesThemeLayoutFromDefaultCloneAndAppliesPresetPurely() {
        layoutRepo.store(layout(DEFAULT, "站点自有首页", new HashMap<>(Map.of(
                "navigationJson", "[{\"name\":\"wiki\"}]", "announcement", "欢迎")), true));

        boolean imported = service.importPluginPreset("neco", "Neco 主题", """
                {"title":"像素首页","subtitle":"像素风","settings":{"homeCss":".pixel{}"}}
                """);

        assertThat(imported).isTrue();
        HomePagePreset preset = presetRepo.findByCode("plugin:neco").orElseThrow();
        assertThat(preset.getSource()).isEqualTo(HomePagePresetSource.PLUGIN);
        assertThat(preset.getThemeCode()).isEqualTo("neco");
        assertThat(preset.getTheme()).isEqualTo("plugin:neco");
        assertThat(preset.getSettings()).containsEntry("homeCss", ".pixel{}")
                .doesNotContainKey("navigationJson");
        HomePageLayout necoLayout = layoutRepo.findByThemeCode("neco").orElseThrow();
        assertThat(necoLayout.getTitle()).isEqualTo("像素首页");
        assertThat(necoLayout.getTheme()).isEqualTo("plugin:neco");
        assertThat(necoLayout.getPublished()).isTrue();
        assertThat(necoLayout.getSettings())
                .containsEntry("homeCss", ".pixel{}")
                .doesNotContainKey("navigationJson")
                .doesNotContainKey("announcement");
        HomePageLayout defaultLayout = layoutRepo.findByThemeCode(DEFAULT).orElseThrow();
        assertThat(defaultLayout.getTitle()).isEqualTo("站点自有首页");
        assertThat(defaultLayout.getSettings())
                .containsEntry("navigationJson", "[{\"name\":\"wiki\"}]")
                .containsEntry("announcement", "欢迎")
                .doesNotContainKey("homeCss");
        assertThat(presetRepo.findBySource(HomePagePresetSource.SNAPSHOT)).isEmpty();
    }

    @Test
    void importPluginPresetUpsertsSamePluginAndPreservesIdentity() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), false));
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
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), false));

        assertThatThrownBy(() -> service.importPluginPreset("neco", "Neco", " "))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.importPluginPreset("neco", "Neco", "{}"))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.importPluginPreset("neco", "Neco", "not-json"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void importPluginPresetAutoAppliesUpgradeWhenLayoutMatchesPreviousPreset() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of("navigationJson", "[]"), true));
        service.importPluginPreset("neco", "Neco", """
                {"title":"v1","settings":{"homeHtml":"<div>old</div>"}}
                """);

        service.importPluginPreset("neco", "Neco", """
                {"title":"v2","settings":{"homeCss":".new{}"}}
                """);

        HomePageLayout necoLayout = layoutRepo.findByThemeCode("neco").orElseThrow();
        assertThat(necoLayout.getTitle()).isEqualTo("v2");
        assertThat(necoLayout.getTheme()).isEqualTo("plugin:neco");
        assertThat(necoLayout.getSettings())
                .containsEntry("homeCss", ".new{}")
                .doesNotContainKey("homeHtml")
                .doesNotContainKey("navigationJson");
    }

    @Test
    void importPluginPresetKeepsAdminEditedLayoutAndOnlyUpdatesPreset() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), true));
        service.importPluginPreset("neco", "Neco", "{\"title\":\"v1\",\"settings\":{\"homeCss\":\".old{}\"}}");
        HomePageLayout edited = layoutRepo.findByThemeCode("neco").orElseThrow();
        edited.setTitle("管理员改过的首页");

        service.importPluginPreset("neco", "Neco", "{\"title\":\"v2\",\"settings\":{\"homeCss\":\".new{}\"}}");

        HomePageLayout necoLayout = layoutRepo.findByThemeCode("neco").orElseThrow();
        assertThat(necoLayout.getTitle()).isEqualTo("管理员改过的首页");
        assertThat(necoLayout.getSettings()).containsEntry("homeCss", ".old{}");
        HomePagePreset preset = presetRepo.findByCode("plugin:neco").orElseThrow();
        assertThat(preset.getTitle()).isEqualTo("v2");
        assertThat(preset.getSettings()).containsEntry("homeCss", ".new{}");
    }

    @Test
    void importPluginPresetPublishesDeclaredPagesIntoOwnThemeWithSourceMark() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), true));

        service.importPluginPreset("neco", "Neco", """
                {"title":"像素首页","pages":[
                  {"slug":"neco-about","title":"关于我们","summary":"像素风","template":"LANDING",
                   "htmlContent":"<section data-yb-html=\\"site.name\\"></section>","cssContent":".a{}"},
                  {"slug":"neco-join","title":"加入我们"}
                ]}
                """);

        CmsPage about = pageRepo.findBySlug("neco", "neco-about").orElseThrow();
        assertThat(about.getThemeCode()).isEqualTo("neco");
        assertThat(about.getStatus()).isEqualTo(PageStatus.PUBLISHED);
        assertThat(about.getSourcePluginCode()).isEqualTo("neco");
        assertThat(about.getTemplate().name()).isEqualTo("LANDING");
        assertThat(about.getPublishedAt()).isNotNull();
        assertThat(pageRepo.findBySlug("neco", "neco-join")).isPresent();
        assertThat(pageRepo.findBySlug(DEFAULT, "neco-about")).isEmpty();
    }

    @Test
    void importPluginPagesSkipsSlugOccupiedByAdminInSameThemeButAllowsCrossThemeReuse() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), true));
        CmsPage necoAdminPage = CmsPage.create("主题内管理员页", "about", "neco");
        necoAdminPage.publish();
        pageRepo.save(necoAdminPage);
        CmsPage defaultAdminPage = CmsPage.create("默认主题页面", "shared", DEFAULT);
        defaultAdminPage.publish();
        pageRepo.save(defaultAdminPage);

        service.importPluginPreset("neco", "Neco", """
                {"title":"像素首页","pages":[
                  {"slug":"about","title":"覆盖尝试"},
                  {"slug":"shared","title":"跨主题复用"}
                ]}
                """);

        assertThat(pageRepo.findBySlug("neco", "about").orElseThrow().getTitle()).isEqualTo("主题内管理员页");
        assertThat(pageRepo.findBySlug(DEFAULT, "shared").orElseThrow().getTitle()).isEqualTo("默认主题页面");
        CmsPage reused = pageRepo.findBySlug("neco", "shared").orElseThrow();
        assertThat(reused.getTitle()).isEqualTo("跨主题复用");
        assertThat(reused.getSourcePluginCode()).isEqualTo("neco");
    }

    @Test
    void importPluginPagesDraftsOwnedPagesDroppedFromDeclaration() {
        layoutRepo.store(layout(DEFAULT, "默认首页", Map.of(), true));
        service.importPluginPreset("neco", "Neco", """
                {"title":"v1","pages":[{"slug":"neco-a","title":"A"},{"slug":"neco-b","title":"B"}]}
                """);
        assertThat(pageRepo.findBySlug("neco", "neco-a").orElseThrow().getStatus()).isEqualTo(PageStatus.PUBLISHED);

        service.importPluginPreset("neco", "Neco", """
                {"title":"v2","pages":[{"slug":"neco-b","title":"B"}]}
                """);

        assertThat(pageRepo.findBySlug("neco", "neco-a").orElseThrow().getStatus()).isEqualTo(PageStatus.DRAFT);
        assertThat(pageRepo.findBySlug("neco", "neco-b").orElseThrow().getStatus()).isEqualTo(PageStatus.PUBLISHED);
    }

    @Test
    void switchingThemesNeverMixesHomeDesigns() {
        layoutRepo.store(layout(DEFAULT, "站点自有首页", new HashMap<>(Map.of(
                "navigationJson", "[{\"name\":\"wiki\"}]", "announcement", "欢迎")), true));
        service.importPluginPreset("neco", "Neco", """
                {"title":"像素首页","settings":{"homeHtml":"<div>neco</div>","homeCss":".neco{}"}}
                """);

        service.importPluginPreset("pixel", "Pixel", """
                {"title":"另一套主题","settings":{"homeCss":".pixel{}"}}
                """);

        HomePageLayout necoLayout = layoutRepo.findByThemeCode("neco").orElseThrow();
        assertThat(necoLayout.getTitle()).isEqualTo("像素首页");
        assertThat(necoLayout.getSettings())
                .containsEntry("homeHtml", "<div>neco</div>")
                .doesNotContainKey("navigationJson");
        HomePageLayout pixelLayout = layoutRepo.findByThemeCode("pixel").orElseThrow();
        assertThat(pixelLayout.getTitle()).isEqualTo("另一套主题");
        assertThat(pixelLayout.getSettings())
                .containsEntry("homeCss", ".pixel{}")
                .doesNotContainKey("homeHtml")
                .doesNotContainKey("announcement");
        HomePageLayout defaultLayout = layoutRepo.findByThemeCode(DEFAULT).orElseThrow();
        assertThat(defaultLayout.getTitle()).isEqualTo("站点自有首页");
        assertThat(defaultLayout.getSettings())
                .containsEntry("navigationJson", "[{\"name\":\"wiki\"}]")
                .containsEntry("announcement", "欢迎")
                .doesNotContainKey("homeHtml")
                .doesNotContainKey("homeCss");
    }

    private HomePagePresetSaveCmd cmd(String name, String description) {
        HomePagePresetSaveCmd cmd = new HomePagePresetSaveCmd();
        cmd.setName(name);
        cmd.setDescription(description);
        return cmd;
    }

    private HomePageLayout layout(String themeCode, String title, Map<String, String> settings, boolean published) {
        return HomePageLayout.builder()
                .themeCode(themeCode)
                .title(title)
                .subtitle("副标题")
                .theme(DEFAULT)
                .settings(new HashMap<>(settings))
                .sections(new ArrayList<>())
                .published(published)
                .build();
    }

    private HomePagePreset.HomePagePresetBuilder<?, ?> preset(String code, String themeCode,
                                                              HomePagePresetSource source) {
        return HomePagePreset.builder()
                .code(code)
                .name(code)
                .source(source)
                .themeCode(themeCode)
                .theme(DEFAULT);
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

        private final Map<String, HomePageLayout> store = new LinkedHashMap<>();

        void store(HomePageLayout layout) {
            store.put(layout.getThemeCode(), layout);
        }

        @Override
        public HomePageLayout save(HomePageLayout layout) {
            store.put(layout.getThemeCode(), layout);
            return layout;
        }

        @Override
        public Optional<HomePageLayout> findByThemeCode(String themeCode) {
            return Optional.ofNullable(store.get(themeCode));
        }

        @Override
        public List<HomePageLayout> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public void deleteById(Long id) {
            store.values().removeIf(layout -> java.util.Objects.equals(layout.getId(), id));
        }
    }

    static class InMemoryCmsPageRepo implements CmsPageRepo {

        private final Map<Long, CmsPage> store = new LinkedHashMap<>();
        private final AtomicLong idSequence = new AtomicLong(1);

        @Override
        public CmsPage save(CmsPage page) {
            if (page.getId() == null) {
                page.setId(idSequence.getAndIncrement());
            }
            store.put(page.getId(), page);
            return page;
        }

        @Override
        public Optional<CmsPage> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<CmsPage> findBySlug(String themeCode, String slug) {
            return store.values().stream()
                    .filter(page -> java.util.Objects.equals(themeCode, page.getThemeCode()))
                    .filter(page -> slug.equals(page.getSlug()))
                    .findFirst();
        }

        @Override
        public List<CmsPage> findBySourcePluginCode(String pluginCode) {
            return store.values().stream()
                    .filter(page -> pluginCode.equals(page.getSourcePluginCode()))
                    .toList();
        }

        @Override
        public void deleteById(Long id) {
            store.remove(id);
        }

        @Override
        public List<CmsPage> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public PageResult<CmsPage> page(String themeCode, String keyword, int page, int size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<CmsPage> publishedPage(String themeCode, String keyword, String category, String tag,
                                                 int page, int size) {
            throw new UnsupportedOperationException();
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
            return sorted(new ArrayList<>(store.values()));
        }

        @Override
        public List<HomePagePreset> findByThemeCode(String themeCode) {
            return sorted(store.values().stream()
                    .filter(preset -> java.util.Objects.equals(themeCode, preset.getThemeCode()))
                    .toList());
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

    static class InMemorySettingRepo implements SettingRepo {

        private final Map<String, Setting> store = new LinkedHashMap<>();

        void setActiveSite(String pluginCode) {
            save(Setting.builder().key("pluginTheme.active.site").value(pluginCode).build());
        }

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
            return store.values().stream()
                    .filter(setting -> category.equals(setting.getCategory()))
                    .toList();
        }

        @Override
        public List<Setting> findAll() {
            return new ArrayList<>(store.values());
        }
    }
}
