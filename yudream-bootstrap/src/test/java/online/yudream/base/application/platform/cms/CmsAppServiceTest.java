package online.yudream.base.application.platform.cms;

import online.yudream.base.application.platform.cms.cmd.CmsPageSaveCmd;
import online.yudream.base.application.platform.cms.cmd.HomePageLayoutSaveCmd;
import online.yudream.base.application.platform.cms.dto.CmsPageDTO;
import online.yudream.base.application.platform.cms.dto.HomePageLayoutDTO;
import online.yudream.base.application.platform.cms.query.CmsPageQuery;
import online.yudream.base.application.platform.cms.service.CmsAppService;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.platform.theme.service.SiteThemeQueryService;
import online.yudream.base.application.platform.theme.service.ThemeConfigAppService;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.theme.service.ThemeConfigSecretCipher;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CmsAppServiceTest {

    private static final String DEFAULT = HomePageLayout.DEFAULT_THEME_CODE;

    private InMemoryCapabilityModuleRepo capabilityModuleRepo;
    private InMemoryCmsPageRepo pageRepo;
    private InMemoryHomePageLayoutRepo layoutRepo;
    private InMemorySettingRepo settingRepo;
    private CmsAppService service;

    @BeforeEach
    void setUp() {
        capabilityModuleRepo = new InMemoryCapabilityModuleRepo();
        pageRepo = new InMemoryCmsPageRepo();
        layoutRepo = new InMemoryHomePageLayoutRepo();
        settingRepo = new InMemorySettingRepo();
        service = new CmsAppService(capabilityModuleRepo, pageRepo, layoutRepo,
                new SiteThemeQueryService(settingRepo),
                new ThemeConfigAppService(settingRepo,
                        org.mockito.Mockito.mock(PluginRuntimeGateway.class, org.mockito.Mockito.CALLS_REAL_METHODS),
                        new NoopCipher(), new ObjectMapper()));
    }

    @Test
    void pageListsOnlyRequestedThemesPages() {
        pageRepo.save(published("默认页", "a", DEFAULT));
        pageRepo.save(published("像素页", "b", "neco"));

        PageResult<CmsPageDTO> necoPages = service.page("neco", query(null));
        PageResult<CmsPageDTO> defaultPages = service.page(DEFAULT, query(null));

        assertThat(necoPages.getRecords()).extracting(CmsPageDTO::getTitle).containsExactly("像素页");
        assertThat(defaultPages.getRecords()).extracting(CmsPageDTO::getTitle).containsExactly("默认页");
    }

    @Test
    void pageDefaultsToActiveSiteTheme() {
        pageRepo.save(published("默认页", "a", DEFAULT));
        pageRepo.save(published("像素页", "b", "neco"));
        settingRepo.setActiveSite("neco");

        PageResult<CmsPageDTO> result = service.page(null, query(null));

        assertThat(result.getRecords()).extracting(CmsPageDTO::getTitle).containsExactly("像素页");
    }

    @Test
    void savePageBindsNewPageToTargetTheme() {
        CmsPageDTO saved = service.savePage("neco", pageCmd(null, "主题页", "hello"));

        assertThat(saved.getThemeCode()).isEqualTo("neco");
        assertThat(pageRepo.findBySlug("neco", "hello")).isPresent();
        assertThat(pageRepo.findBySlug(DEFAULT, "hello")).isEmpty();
    }

    @Test
    void sameSlugAllowedAcrossThemesButUniqueWithinTheme() {
        service.savePage(DEFAULT, pageCmd(null, "默认关于", "about"));
        service.savePage("neco", pageCmd(null, "像素关于", "about"));

        assertThat(pageRepo.findBySlug(DEFAULT, "about").orElseThrow().getTitle()).isEqualTo("默认关于");
        assertThat(pageRepo.findBySlug("neco", "about").orElseThrow().getTitle()).isEqualTo("像素关于");
        assertThatThrownBy(() -> service.savePage("neco", pageCmd(null, "冲突", "about")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("页面路径已存在");
    }

    @Test
    void updatePageKeepsItsOwnThemeScopeForSlugCheck() {
        CmsPageDTO created = service.savePage("neco", pageCmd(null, "原题", "hello"));
        service.savePage("neco", pageCmd(null, "占位", "taken"));

        CmsPageSaveCmd update = pageCmd(created.getId(), "新题", "hello");
        CmsPageDTO updated = service.savePage(null, update);

        assertThat(updated.getTitle()).isEqualTo("新题");
        assertThat(updated.getThemeCode()).isEqualTo("neco");
        CmsPageSaveCmd conflict = pageCmd(created.getId(), "新题", "taken");
        assertThatThrownBy(() -> service.savePage(null, conflict))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("页面路径已存在");
    }

    @Test
    void saveHomeLayoutWritesOnlyTargetThemeAndKeepsAppliedPresetMarker() {
        layoutRepo.store(layout(DEFAULT, "默认首页", "default", true));
        layoutRepo.store(layout("neco", "像素首页", "plugin:neco", true));

        HomePageLayoutSaveCmd cmd = new HomePageLayoutSaveCmd();
        cmd.setTitle("像素首页 v2");
        cmd.setSubtitle("副标题");
        cmd.setTheme("user-999");
        cmd.setSettings(Map.of("homeCss", ".x{}"));
        cmd.setPublished(false);
        service.saveHomeLayout("neco", cmd);

        HomePageLayout necoLayout = layoutRepo.findByThemeCode("neco").orElseThrow();
        assertThat(necoLayout.getTitle()).isEqualTo("像素首页 v2");
        assertThat(necoLayout.getTheme()).isEqualTo("plugin:neco");
        assertThat(necoLayout.getPublished()).isFalse();
        HomePageLayout defaultLayout = layoutRepo.findByThemeCode(DEFAULT).orElseThrow();
        assertThat(defaultLayout.getTitle()).isEqualTo("默认首页");
    }

    @Test
    void publicHomeReadsActiveThemeOnlyAndNeverFallsBack() {
        layoutRepo.store(layout(DEFAULT, "默认首页", "default", true));
        settingRepo.setActiveSite("neco");

        assertThatThrownBy(() -> service.publicHome())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("首页未配置");

        layoutRepo.store(layout("neco", "像素首页", "plugin:neco", false));
        assertThatThrownBy(() -> service.publicHome())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("首页未发布");

        layoutRepo.store(layout("neco", "像素首页", "plugin:neco", true));
        assertThat(service.publicHome().getTitle()).isEqualTo("像素首页");
    }

    @Test
    void publicPageReadsActiveThemeOnly() {
        pageRepo.save(published("默认关于", "about", DEFAULT));

        assertThat(service.publicPage("about").getTitle()).isEqualTo("默认关于");

        settingRepo.setActiveSite("neco");
        assertThatThrownBy(() -> service.publicPage("about"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("页面不存在");
    }

    @Test
    void deactivatedThemePagesStayPublishedButInvisible() {
        CmsPage necoPage = published("像素页", "neco-page", "neco");
        pageRepo.save(necoPage);
        settingRepo.setActiveSite("neco");
        assertThat(service.publicPage("neco-page").getTitle()).isEqualTo("像素页");

        settingRepo.setActiveSite("");
        assertThatThrownBy(() -> service.publicPage("neco-page"))
                .isInstanceOf(BizException.class);
        assertThat(pageRepo.findBySlug("neco", "neco-page").orElseThrow().getStatus())
                .isEqualTo(PageStatus.PUBLISHED);
    }

    @Test
    void publicPagesFiltersByActiveThemeAndPublishedStatus() {
        pageRepo.save(published("默认公开", "a", DEFAULT));
        CmsPage necoPublished = published("像素公开", "b", "neco");
        pageRepo.save(necoPublished);
        CmsPage necoDraft = CmsPage.create("像素草稿", "c", "neco");
        pageRepo.save(necoDraft);
        settingRepo.setActiveSite("neco");

        PageResult<CmsPageDTO> result = service.publicPages(query(null));

        assertThat(result.getRecords()).extracting(CmsPageDTO::getTitle).containsExactly("像素公开");
    }

    @Test
    void capabilityDisabledRejectsAllOperations() {
        capabilityModuleRepo.enabled = false;

        assertThatThrownBy(() -> service.page(DEFAULT, query(null))).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.homeLayout(DEFAULT)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.publicHome()).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> service.publicPage("a")).isInstanceOf(BizException.class);
    }

    private CmsPageQuery query(String keyword) {
        CmsPageQuery query = new CmsPageQuery();
        query.setKeyword(keyword);
        query.setPage(1);
        query.setSize(10);
        return query;
    }

    private CmsPageSaveCmd pageCmd(Long id, String title, String slug) {
        CmsPageSaveCmd cmd = new CmsPageSaveCmd();
        cmd.setId(id);
        cmd.setTitle(title);
        cmd.setSlug(slug);
        return cmd;
    }

    private CmsPage published(String title, String slug, String themeCode) {
        CmsPage page = CmsPage.create(title, slug, themeCode);
        page.publish();
        return page;
    }

    private HomePageLayout layout(String themeCode, String title, String appliedPreset, boolean published) {
        return HomePageLayout.builder()
                .themeCode(themeCode)
                .title(title)
                .subtitle("副标题")
                .theme(appliedPreset)
                .settings(new HashMap<>())
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
            List<CmsPage> records = store.values().stream()
                    .filter(item -> java.util.Objects.equals(themeCode, item.getThemeCode()))
                    .filter(item -> keyword == null || item.getTitle().contains(keyword))
                    .toList();
            return new PageResult<>(records, records.size(), page, size);
        }

        @Override
        public PageResult<CmsPage> publishedPage(String themeCode, String keyword, String category, String tag,
                                                 int page, int size) {
            List<CmsPage> records = store.values().stream()
                    .filter(item -> java.util.Objects.equals(themeCode, item.getThemeCode()))
                    .filter(item -> item.getStatus() == PageStatus.PUBLISHED)
                    .toList();
            return new PageResult<>(records, records.size(), page, size);
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
    private static class NoopCipher implements ThemeConfigSecretCipher {
        @Override
        public boolean canEncrypt() {
            return false;
        }

        @Override
        public boolean encrypted(String value) {
            return false;
        }

        @Override
        public String encrypt(String themeCode, String fieldKey, String plaintext) {
            return plaintext;
        }
    }
}
