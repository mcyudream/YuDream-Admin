package online.yudream.base.application.platform.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.cms.assembler.CmsAssembler;
import online.yudream.base.application.platform.cms.cmd.HomePagePresetSaveCmd;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;
import online.yudream.base.application.platform.cms.dto.PluginHomePresetPayload;
import online.yudream.base.application.platform.cms.dto.PluginPresetPagePayload;
import online.yudream.base.application.platform.theme.service.SiteThemeQueryService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.CmsPage;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.aggregate.HomePagePreset;
import online.yudream.base.domain.platform.cms.enumerate.HomePagePresetSource;
import online.yudream.base.domain.platform.cms.enumerate.PageStatus;
import online.yudream.base.domain.platform.cms.repo.CmsPageRepo;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import online.yudream.base.domain.platform.cms.repo.HomePagePresetRepo;
import online.yudream.base.domain.platform.cms.valobj.PageSlug;
import online.yudream.base.domain.shared.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 首页内容定制方案编排：方案按主题（themeCode）完全隔离，只允许应用到其归属主题的
 * 首页布局。任何切换（含插件主题自带方案）都会先对该主题当前定制做自动快照，保证可回滚；
 * 自动快照与最近一份内容一致时跳过，每个主题最多保留 {@value #MAX_SNAPSHOTS} 份。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsPresetAppService {

    private static final String CAPABILITY_CODE = "cms";
    private static final int MAX_SNAPSHOTS = 10;
    private static final DateTimeFormatter SNAPSHOT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final HomePageLayoutRepo homePageLayoutRepo;
    private final HomePagePresetRepo homePagePresetRepo;
    private final CmsPageRepo cmsPageRepo;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final SiteThemeQueryService siteThemeQueryService;

    /**
     * 列出指定主题（null=当前激活主题）的首页方案，active 标记与该主题布局比对。
     */
    @Transactional(readOnly = true)
    public List<HomePagePresetDTO> list(String themeCode) {
        ensureEnabled();
        String theme = resolveTheme(themeCode);
        String appliedPresetCode = layoutOf(theme).getTheme();
        return homePagePresetRepo.findByThemeCode(theme).stream()
                .map(preset -> {
                    HomePagePresetDTO dto = CmsAssembler.toPresetDTO(preset);
                    dto.setActive(preset.getCode().equals(appliedPresetCode));
                    return dto;
                })
                .toList();
    }

    /**
     * 把指定主题（null=当前激活主题）的当前首页定制存为该主题的用户方案。
     */
    @Transactional
    public HomePagePresetDTO saveCurrentAsPreset(String themeCode, HomePagePresetSaveCmd cmd) {
        ensureEnabled();
        if (cmd == null || !StringUtils.hasText(cmd.getName())) {
            throw new BizException("方案名称不能为空");
        }
        HomePageLayout current = layoutOf(resolveTheme(themeCode));
        HomePagePreset preset = HomePagePreset.snapshotOf(
                "user-" + idGenerator.nextId(), cmd.getName().trim(), cmd.getDescription(),
                HomePagePresetSource.USER, null, current);
        return CmsAssembler.toPresetDTO(homePagePresetRepo.save(preset));
    }

    /**
     * 一键切换：先对方案归属主题的当前定制做自动快照，再把方案整体回写为该主题首页
     * （保留发布状态）。方案只允许应用到其归属主题。
     */
    @Transactional
    public void apply(String code) {
        ensureEnabled();
        HomePagePreset preset = homePagePresetRepo.findByCode(code)
                .orElseThrow(() -> new BizException("方案不存在"));
        HomePageLayout current = layoutOf(preset.getThemeCode());
        snapshotCurrentIfChanged(current);
        applyPresetToLayout(current, preset);
        homePageLayoutRepo.save(current);
    }

    @Transactional
    public void delete(String code) {
        ensureEnabled();
        homePagePresetRepo.findByCode(code).orElseThrow(() -> new BizException("方案不存在"));
        homePagePresetRepo.deleteByCode(code);
    }

    /**
     * 确保主题持有一套首页布局：没有布局的主题（未声明首页方案的插件主题激活时）
     * 克隆内置默认主题的当前首页作为起点，此后完全独立编辑、互不影响。
     */
    @Transactional
    public void ensureThemeLayout(String themeCode) {
        if (!capabilityEnabled()) {
            log.info("内容定制能力未启用，跳过主题首页布局初始化：{}", themeCode);
            return;
        }
        if (homePageLayoutRepo.findByThemeCode(themeCode).isPresent()) {
            return;
        }
        HomePageLayout base = homePageLayoutRepo.findByThemeCode(HomePageLayout.DEFAULT_THEME_CODE)
                .orElseGet(() -> HomePageLayout.defaultLayout(HomePageLayout.DEFAULT_THEME_CODE));
        HomePageLayout clone = HomePageLayout.builder()
                .themeCode(themeCode)
                .title(base.getTitle())
                .subtitle(base.getSubtitle())
                .theme(base.getTheme())
                .heroImageUrl(base.getHeroImageUrl())
                .settings(base.getSettings() == null ? new HashMap<>() : new HashMap<>(base.getSettings()))
                .sections(base.getSections() == null ? new ArrayList<>() : new ArrayList<>(base.getSections()))
                .published(base.getPublished())
                .build();
        homePageLayoutRepo.save(clone);
        log.info("主题无首页布局，已克隆默认主题当前首页作为起点：theme={}", themeCode);
    }

    /**
     * 导入插件主题自带的首页方案与页面集。每个主题的内容完全独立：方案 payload 纯量
     * upsert 为 code=plugin:{pluginCode}、themeCode={pluginCode} 的方案，不与任何其他
     * 主题的内容合并。该主题尚无布局时先克隆默认主题首页再应用方案；已有布局且内容与
     * 上一版方案一致（管理员未改动）时自动应用新方案，升级无残留；已被管理员改动时
     * 只更新方案、不动内容（方案列表可一键应用）。页面集按 (主题, slug) 归属管理：
     * 本主题内 slug 未被占用或属于该插件时创建/覆盖为已发布；被管理员占用时跳过；
     * 声明清单之外的该插件旧页面转草稿。内容定制能力关闭时不导入，返回 false。
     */
    @Transactional
    public boolean importPluginPreset(String pluginCode, String presetName, String presetJson) {
        if (!capabilityEnabled()) {
            log.info("内容定制能力未启用，跳过插件首页方案导入：{}", pluginCode);
            return false;
        }
        PluginHomePresetPayload payload = parsePayload(pluginCode, presetJson);
        String code = pluginPresetCode(pluginCode);
        Optional<HomePagePreset> previous = homePagePresetRepo.findByCode(code);
        HomePagePreset preset = HomePagePreset.builder()
                .code(code)
                .name(StringUtils.hasText(presetName) ? presetName : pluginCode)
                .description("插件主题自带方案")
                .source(HomePagePresetSource.PLUGIN)
                .pluginCode(pluginCode)
                .themeCode(pluginCode)
                .theme(code)
                .title(payload.getTitle())
                .subtitle(payload.getSubtitle())
                .heroImageUrl(payload.getHeroImageUrl())
                .settings(payload.getSettings() == null ? Map.of() : new HashMap<>(payload.getSettings()))
                .sections(payload.getSections() == null ? List.of() : new ArrayList<>(payload.getSections()))
                .build();
        previous.ifPresent(existing -> {
            preset.setId(existing.getId());
            preset.setCreateTime(existing.getCreateTime());
        });
        homePagePresetRepo.save(preset);

        Optional<HomePageLayout> existingLayout = homePageLayoutRepo.findByThemeCode(pluginCode);
        if (existingLayout.isEmpty()) {
            ensureThemeLayout(pluginCode);
            HomePageLayout layout = homePageLayoutRepo.findByThemeCode(pluginCode)
                    .orElseGet(() -> HomePageLayout.defaultLayout(pluginCode));
            applyPresetToLayout(layout, preset);
            homePageLayoutRepo.save(layout);
        } else if (previous.map(old -> old.sameContent(existingLayout.get())).orElse(false)) {
            HomePageLayout layout = existingLayout.get();
            applyPresetToLayout(layout, preset);
            homePageLayoutRepo.save(layout);
            log.info("主题首页未被改动，已自动应用新版自带方案：plugin={}", pluginCode);
        } else {
            log.info("主题首页已被管理员改动，仅更新自带方案不覆盖内容：plugin={}", pluginCode);
        }
        importPluginPages(pluginCode, payload.getPages());
        return true;
    }

    private void importPluginPages(String pluginCode, List<PluginPresetPagePayload> payloads) {
        if (payloads == null) {
            return;
        }
        Set<String> declaredSlugs = new HashSet<>();
        for (PluginPresetPagePayload payload : payloads) {
            if (payload == null || !StringUtils.hasText(payload.getSlug()) || !StringUtils.hasText(payload.getTitle())) {
                log.warn("主题页面缺少 slug 或标题，跳过：plugin={}", pluginCode);
                continue;
            }
            String slug;
            try {
                slug = PageSlug.of(payload.getSlug()).value();
            } catch (BizException e) {
                log.warn("主题页面路径非法，跳过：plugin={}, slug={}", pluginCode, payload.getSlug());
                continue;
            }
            declaredSlugs.add(slug);
            Optional<CmsPage> existing = cmsPageRepo.findBySlug(pluginCode, slug);
            if (existing.isPresent() && !pluginCode.equals(existing.get().getSourcePluginCode())) {
                log.warn("主题页面路径已被该主题下管理员内容占用，跳过导入：plugin={}, slug={}", pluginCode, slug);
                continue;
            }
            CmsPage page = existing.orElseGet(() -> CmsPage.create(payload.getTitle(), slug, pluginCode));
            page.update(payload.getTitle(), slug, payload.getSummary(), null, payload.getCoverImageUrl(),
                    null, null, payload.getMarkdownContent(), payload.getHtmlContent(), payload.getCssContent(),
                    payload.getJsContent(), null, payload.getSeoTitle(), payload.getSeoDescription(),
                    payload.getTemplate(), PageStatus.PUBLISHED);
            page.setSourcePluginCode(pluginCode);
            cmsPageRepo.save(page);
        }
        for (CmsPage page : cmsPageRepo.findBySourcePluginCode(pluginCode)) {
            if (!declaredSlugs.contains(page.getSlug()) && page.getStatus() == PageStatus.PUBLISHED) {
                page.unpublish();
                cmsPageRepo.save(page);
                log.info("主题页面已不在声明清单内，转草稿：plugin={}, slug={}", pluginCode, page.getSlug());
            }
        }
    }

    private void applyPresetToLayout(HomePageLayout layout, HomePagePreset preset) {
        layout.update(preset.getTitle(), preset.getSubtitle(), preset.getCode(), preset.getHeroImageUrl(),
                preset.getSettings(), preset.getSections(), layout.getPublished());
    }

    private void snapshotCurrentIfChanged(HomePageLayout current) {
        List<HomePagePreset> snapshots = homePagePresetRepo.findBySource(HomePagePresetSource.SNAPSHOT);
        // createTime 在毫秒级精度下可能并列，新旧判定一律走单调递增的 id
        List<HomePagePreset> newestFirst = snapshots.stream()
                .filter(snapshot -> snapshot.getId() != null)
                .filter(snapshot -> java.util.Objects.equals(snapshot.getThemeCode(), current.getThemeCode()))
                .sorted(Comparator.comparing(HomePagePreset::getId).reversed())
                .toList();
        if (!newestFirst.isEmpty() && newestFirst.getFirst().sameContent(current)) {
            return;
        }
        HomePagePreset snapshot = HomePagePreset.snapshotOf(
                "snapshot-" + idGenerator.nextId(),
                "切换前快照 " + SNAPSHOT_NAME_FORMAT.format(LocalDateTime.now()),
                "应用方案前自动保存的首页定制",
                HomePagePresetSource.SNAPSHOT, null, current);
        homePagePresetRepo.save(snapshot);
        newestFirst.stream().skip(MAX_SNAPSHOTS - 1L)
                .forEach(stale -> homePagePresetRepo.deleteByCode(stale.getCode()));
    }

    private PluginHomePresetPayload parsePayload(String pluginCode, String presetJson) {
        if (!StringUtils.hasText(presetJson)) {
            throw new BizException("插件首页方案内容为空：" + pluginCode);
        }
        try {
            PluginHomePresetPayload payload = objectMapper.readValue(presetJson, PluginHomePresetPayload.class);
            if (payload.getTitle() == null && payload.getSubtitle() == null && payload.getHeroImageUrl() == null
                    && payload.getSettings() == null && payload.getSections() == null && payload.getPages() == null) {
                throw new BizException("插件首页方案未声明任何内容：" + pluginCode);
            }
            return payload;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("插件首页方案解析失败：" + pluginCode);
        }
    }

    private HomePageLayout layoutOf(String themeCode) {
        return homePageLayoutRepo.findByThemeCode(themeCode)
                .orElseGet(() -> HomePageLayout.defaultLayout(themeCode));
    }

    private String resolveTheme(String themeCode) {
        return StringUtils.hasText(themeCode) ? themeCode : siteThemeQueryService.activeSiteThemeCode();
    }

    private String pluginPresetCode(String pluginCode) {
        return "plugin:" + pluginCode;
    }

    private boolean capabilityEnabled() {
        return capabilityModuleRepo.findByCode(CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
    }

    private void ensureEnabled() {
        if (!capabilityEnabled()) {
            throw new BizException("内容定制能力未启用");
        }
    }
}
