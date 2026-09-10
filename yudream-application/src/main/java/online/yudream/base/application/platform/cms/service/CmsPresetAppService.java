package online.yudream.base.application.platform.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.cms.assembler.CmsAssembler;
import online.yudream.base.application.platform.cms.cmd.HomePagePresetSaveCmd;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;
import online.yudream.base.application.platform.cms.dto.PluginHomePresetPayload;
import online.yudream.base.application.platform.cms.dto.PluginPresetPagePayload;
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
import java.util.Comparator;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 首页内容定制方案编排：把当前首页定制存为可命名方案，一键切换。
 * 任何切换（含插件主题自带方案）都会先对当前定制做自动快照，保证可回滚；
 * 自动快照与最近一份内容一致时跳过，最多保留 {@value #MAX_SNAPSHOTS} 份。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CmsPresetAppService {

    private static final String CAPABILITY_CODE = "cms";
    private static final int MAX_SNAPSHOTS = 10;
    /** 主题接管前的首页备份方案编码前缀；备份仅用于停用还原，不出现在方案列表。 */
    private static final String PLUGIN_BACKUP_PREFIX = "plugin-backup:";
    private static final String DEFAULT_THEME = "default";
    private static final DateTimeFormatter SNAPSHOT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final HomePageLayoutRepo homePageLayoutRepo;
    private final HomePagePresetRepo homePagePresetRepo;
    private final CmsPageRepo cmsPageRepo;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<HomePagePresetDTO> list() {
        ensureEnabled();
        String currentTheme = currentLayout().getTheme();
        return homePagePresetRepo.findAll().stream()
                .filter(preset -> preset.getCode() == null || !preset.getCode().startsWith(PLUGIN_BACKUP_PREFIX))
                .map(preset -> {
                    HomePagePresetDTO dto = CmsAssembler.toPresetDTO(preset);
                    dto.setActive(preset.getCode().equals(currentTheme));
                    return dto;
                })
                .toList();
    }

    /**
     * 把当前首页定制存为用户方案。
     */
    @Transactional
    public HomePagePresetDTO saveCurrentAsPreset(HomePagePresetSaveCmd cmd) {
        ensureEnabled();
        if (cmd == null || !StringUtils.hasText(cmd.getName())) {
            throw new BizException("方案名称不能为空");
        }
        HomePageLayout current = currentLayout();
        HomePagePreset preset = HomePagePreset.snapshotOf(
                "user-" + idGenerator.nextId(), cmd.getName().trim(), cmd.getDescription(),
                HomePagePresetSource.USER, null, current);
        return CmsAssembler.toPresetDTO(homePagePresetRepo.save(preset));
    }

    /**
     * 一键切换：先自动快照当前定制，再把方案整体回写为当前首页（保留发布状态）。
     */
    @Transactional
    public void apply(String code) {
        ensureEnabled();
        HomePagePreset preset = homePagePresetRepo.findByCode(code)
                .orElseThrow(() -> new BizException("方案不存在"));
        applyInternal(preset);
    }

    @Transactional
    public void delete(String code) {
        ensureEnabled();
        homePagePresetRepo.findByCode(code).orElseThrow(() -> new BizException("方案不存在"));
        homePagePresetRepo.deleteByCode(code);
    }

    /**
     * 导入插件主题自带的首页方案与页面集并应用。每个主题的首页设计彼此独立：
     * 导入前先把布局还原到「主题接管前」的基准（上一个主题占用时取其接管前备份，
     * 同主题重复导入取自身备份），再以基准覆盖更新 code=plugin:{pluginCode} 的方案并应用，
     * 因此不同主题的 homeHtml/homeCss 等内容绝不混杂；方案只覆盖其声明的字段与 settings
     * 键，未声明的（如站点导航 navigationJson）保留站点在主题之外的自有值。接管前基准
     * 存为 plugin-backup:{pluginCode} 备份方案，供停用时整体还原。页面集按 slug 归属管理：
     * slug 未被占用或属于该插件时创建/覆盖为已发布；被管理员或其他主题占用时跳过；
     * 声明清单之外的该插件旧页面转草稿。内容定制能力关闭时不导入，返回 false。
     */
    @Transactional
    public boolean importPluginPreset(String pluginCode, String presetName, String presetJson) {
        if (!capabilityEnabled()) {
            log.info("内容定制能力未启用，跳过插件首页方案导入：{}", pluginCode);
            return false;
        }
        PluginHomePresetPayload payload = parsePayload(pluginCode, presetJson);
        HomePageLayout base = resolveThemeBaseLayout(pluginCode);
        upsertThemeBackup(pluginCode, presetName, base);
        Map<String, String> settings = new HashMap<>(
                base.getSettings() == null ? Map.of() : base.getSettings());
        if (payload.getSettings() != null) {
            settings.putAll(payload.getSettings());
        }
        HomePagePreset preset = HomePagePreset.builder()
                .code(pluginPresetCode(pluginCode))
                .name(StringUtils.hasText(presetName) ? presetName : pluginCode)
                .description("插件主题自带方案")
                .source(HomePagePresetSource.PLUGIN)
                .pluginCode(pluginCode)
                .title(payload.getTitle() != null ? payload.getTitle() : base.getTitle())
                .subtitle(payload.getSubtitle() != null ? payload.getSubtitle() : base.getSubtitle())
                .heroImageUrl(payload.getHeroImageUrl() != null ? payload.getHeroImageUrl() : base.getHeroImageUrl())
                .settings(settings)
                .sections(payload.getSections() != null ? payload.getSections() : base.getSections())
                .build();
        homePagePresetRepo.findByCode(preset.getCode()).ifPresent(existing -> {
            preset.setId(existing.getId());
            preset.setCreateTime(existing.getCreateTime());
        });
        applyInternal(homePagePresetRepo.save(preset));
        importPluginPages(pluginCode, payload.getPages());
        return true;
    }

    /**
     * 主题停用/顶替/卸载时还原首页设计：当前首页仍由该主题方案占用时，整体还原为该主题
     * 接管前备份的布局（无备份的旧数据退化为剥离该主题方案声明的 settings 键并复位 theme，
     * 保证主题内容不残留）；备份方案随后删除。当前首页已被管理员手工切走时只清理备份。
     */
    @Transactional
    public void restorePluginHomepage(String pluginCode) {
        if (!capabilityEnabled()) {
            log.info("内容定制能力未启用，跳过主题首页还原：{}", pluginCode);
            return;
        }
        Optional<HomePagePreset> backup = homePagePresetRepo.findByCode(pluginBackupCode(pluginCode));
        HomePageLayout current = currentLayout();
        if (pluginPresetCode(pluginCode).equals(current.getTheme())) {
            if (backup.isPresent()) {
                HomePagePreset restored = backup.get();
                current.update(restored.getTitle(), restored.getSubtitle(),
                        StringUtils.hasText(restored.getTheme()) ? restored.getTheme() : DEFAULT_THEME,
                        restored.getHeroImageUrl(), restored.getSettings(), restored.getSections(),
                        current.getPublished());
                homePageLayoutRepo.save(current);
                log.info("主题停用，首页设计已还原到接管前状态：plugin={}", pluginCode);
            } else {
                Map<String, String> settings = new HashMap<>(
                        current.getSettings() == null ? Map.of() : current.getSettings());
                homePagePresetRepo.findByCode(pluginPresetCode(pluginCode))
                        .map(HomePagePreset::getSettings)
                        .ifPresent(declared -> declared.keySet().forEach(settings::remove));
                current.update(current.getTitle(), current.getSubtitle(), DEFAULT_THEME, current.getHeroImageUrl(),
                        settings, current.getSections(), current.getPublished());
                homePageLayoutRepo.save(current);
                log.info("主题停用且无接管前备份，已剥离其声明的首页设置键：plugin={}", pluginCode);
            }
        }
        backup.ifPresent(ignored -> homePagePresetRepo.deleteByCode(pluginBackupCode(pluginCode)));
    }

    /**
     * 主题停用/顶替/卸载时，把该主题随附的页面全部下线转草稿（管理员内容不动）。
     * 再次启用主题时导入流程会把声明的页面恢复为已发布。
     */
    @Transactional
    public void unpublishPluginPages(String pluginCode) {
        if (!capabilityEnabled()) {
            log.info("内容定制能力未启用，跳过主题页面下线：{}", pluginCode);
            return;
        }
        int unpublished = 0;
        for (CmsPage page : cmsPageRepo.findBySourcePluginCode(pluginCode)) {
            if (page.getStatus() == PageStatus.PUBLISHED) {
                page.unpublish();
                cmsPageRepo.save(page);
                unpublished++;
            }
        }
        if (unpublished > 0) {
            log.info("主题页面已下线转草稿：plugin={}, count={}", pluginCode, unpublished);
        }
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
            Optional<CmsPage> existing = cmsPageRepo.findBySlug(slug);
            if (existing.isPresent() && !pluginCode.equals(existing.get().getSourcePluginCode())) {
                log.warn("主题页面路径已被站点或其他主题占用，跳过导入：plugin={}, slug={}", pluginCode, slug);
                continue;
            }
            CmsPage page = existing.orElseGet(() -> CmsPage.create(payload.getTitle(), slug));
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

    /**
     * 解析主题导入的基准布局（即「主题接管前」的站点设计）：
     * 当前首页未被任何主题占用时用当前布局；被本主题占用时用自身接管前备份（升级再导入
     * 不残留旧版本内容）；被其他主题占用时用其接管前备份，保证新主题拿不到上一个主题的
     * 任何内容。旧数据无备份时退化为剥离占用主题方案声明的 settings 键后的当前布局。
     */
    private HomePageLayout resolveThemeBaseLayout(String pluginCode) {
        HomePageLayout current = currentLayout();
        String appliedPlugin = appliedPluginCodeOf(current);
        if (appliedPlugin == null) {
            return current;
        }
        return homePagePresetRepo.findByCode(pluginBackupCode(appliedPlugin))
                .map(this::backupAsLayout)
                .orElseGet(() -> stripThemeSettings(current, appliedPlugin));
    }

    private void upsertThemeBackup(String pluginCode, String presetName, HomePageLayout base) {
        String themeName = StringUtils.hasText(presetName) ? presetName : pluginCode;
        HomePagePreset backup = HomePagePreset.snapshotOf(
                pluginBackupCode(pluginCode),
                "「" + themeName + "」接管前首页",
                "主题停用时自动还原的首页备份",
                HomePagePresetSource.THEME_BACKUP, pluginCode, base);
        homePagePresetRepo.findByCode(backup.getCode()).ifPresent(existing -> {
            backup.setId(existing.getId());
            backup.setCreateTime(existing.getCreateTime());
        });
        homePagePresetRepo.save(backup);
    }

    private String appliedPluginCodeOf(HomePageLayout layout) {
        String theme = layout.getTheme();
        return theme != null && theme.startsWith("plugin:") ? theme.substring("plugin:".length()) : null;
    }

    private HomePageLayout backupAsLayout(HomePagePreset backup) {
        return HomePageLayout.builder()
                .title(backup.getTitle())
                .subtitle(backup.getSubtitle())
                .heroImageUrl(backup.getHeroImageUrl())
                .settings(backup.getSettings() == null ? new HashMap<>() : new HashMap<>(backup.getSettings()))
                .sections(backup.getSections() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(backup.getSections()))
                .build();
    }

    private HomePageLayout stripThemeSettings(HomePageLayout current, String appliedPlugin) {
        Map<String, String> settings = new HashMap<>(
                current.getSettings() == null ? Map.of() : current.getSettings());
        homePagePresetRepo.findByCode(pluginPresetCode(appliedPlugin))
                .map(HomePagePreset::getSettings)
                .ifPresent(declared -> declared.keySet().forEach(settings::remove));
        return HomePageLayout.builder()
                .title(current.getTitle())
                .subtitle(current.getSubtitle())
                .heroImageUrl(current.getHeroImageUrl())
                .settings(settings)
                .sections(current.getSections())
                .build();
    }

    private void applyInternal(HomePagePreset preset) {
        HomePageLayout current = currentLayout();
        snapshotCurrentIfChanged(current);
        current.update(preset.getTitle(), preset.getSubtitle(), preset.getCode(), preset.getHeroImageUrl(),
                preset.getSettings(), preset.getSections(), current.getPublished());
        homePageLayoutRepo.save(current);
    }

    private void snapshotCurrentIfChanged(HomePageLayout current) {
        List<HomePagePreset> snapshots = homePagePresetRepo.findBySource(HomePagePresetSource.SNAPSHOT);
        // createTime 在毫秒级精度下可能并列，新旧判定一律走单调递增的 id
        List<HomePagePreset> newestFirst = snapshots.stream()
                .filter(snapshot -> snapshot.getId() != null)
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

    private HomePageLayout currentLayout() {
        return homePageLayoutRepo.findCurrent().orElseGet(HomePageLayout::defaultLayout);
    }

    private String pluginPresetCode(String pluginCode) {
        return "plugin:" + pluginCode;
    }

    private String pluginBackupCode(String pluginCode) {
        return PLUGIN_BACKUP_PREFIX + pluginCode;
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
