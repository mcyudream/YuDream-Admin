package online.yudream.base.application.platform.cms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.cms.assembler.CmsAssembler;
import online.yudream.base.application.platform.cms.cmd.HomePagePresetSaveCmd;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;
import online.yudream.base.application.platform.cms.dto.PluginHomePresetPayload;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.platform.cms.aggregate.HomePagePreset;
import online.yudream.base.domain.platform.cms.enumerate.HomePagePresetSource;
import online.yudream.base.domain.platform.cms.repo.HomePageLayoutRepo;
import online.yudream.base.domain.platform.cms.repo.HomePagePresetRepo;
import online.yudream.base.domain.shared.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final DateTimeFormatter SNAPSHOT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final HomePageLayoutRepo homePageLayoutRepo;
    private final HomePagePresetRepo homePagePresetRepo;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<HomePagePresetDTO> list() {
        ensureEnabled();
        return homePagePresetRepo.findAll().stream().map(CmsAssembler::toPresetDTO).toList();
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
     * 导入插件主题自带的首页方案并应用。方案只覆盖其声明的字段与 settings 键，
     * 未声明的（如站点导航 navigationJson）保留站点现状；同插件重复启用时按
     * code=plugin:{pluginCode} 覆盖更新。内容定制能力关闭时不导入，返回 false。
     */
    @Transactional
    public boolean importPluginPreset(String pluginCode, String presetName, String presetJson) {
        if (!capabilityEnabled()) {
            log.info("内容定制能力未启用，跳过插件首页方案导入：{}", pluginCode);
            return false;
        }
        PluginHomePresetPayload payload = parsePayload(pluginCode, presetJson);
        HomePageLayout current = currentLayout();
        Map<String, String> settings = new HashMap<>(
                current.getSettings() == null ? Map.of() : current.getSettings());
        if (payload.getSettings() != null) {
            settings.putAll(payload.getSettings());
        }
        HomePagePreset preset = HomePagePreset.builder()
                .code(pluginPresetCode(pluginCode))
                .name(StringUtils.hasText(presetName) ? presetName : pluginCode)
                .description("插件主题自带方案")
                .source(HomePagePresetSource.PLUGIN)
                .pluginCode(pluginCode)
                .title(payload.getTitle() != null ? payload.getTitle() : current.getTitle())
                .subtitle(payload.getSubtitle() != null ? payload.getSubtitle() : current.getSubtitle())
                .heroImageUrl(payload.getHeroImageUrl() != null ? payload.getHeroImageUrl() : current.getHeroImageUrl())
                .settings(settings)
                .sections(payload.getSections() != null ? payload.getSections() : current.getSections())
                .build();
        homePagePresetRepo.findByCode(preset.getCode()).ifPresent(existing -> {
            preset.setId(existing.getId());
            preset.setCreateTime(existing.getCreateTime());
        });
        applyInternal(homePagePresetRepo.save(preset));
        return true;
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
                    && payload.getSettings() == null && payload.getSections() == null) {
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
