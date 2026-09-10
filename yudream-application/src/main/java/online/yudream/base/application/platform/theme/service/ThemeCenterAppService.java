package online.yudream.base.application.platform.theme.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.cms.dto.HomePagePresetDTO;
import online.yudream.base.application.platform.cms.dto.PluginHomePresetPayload;
import online.yudream.base.application.platform.cms.service.CmsPresetAppService;
import online.yudream.base.application.platform.plugin.dto.PluginThemeDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.platform.plugin.service.PluginThemeAppService;
import online.yudream.base.application.platform.theme.dto.ThemeCenterOverviewDTO;
import online.yudream.base.application.platform.theme.dto.ThemeCenterThemeCardDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.plugin.aggregate.PluginModule;
import online.yudream.base.domain.platform.plugin.repo.PluginModuleRepo;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginThemeInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主题中心编排：聚合公开站（SITE）可切换主题（内置默认 + 插件主题），
 * 并收纳首页内容定制方案，实现主题与多套 CMS 首页的自由切换。
 * 切换主题即启用/禁用对应主题插件，互斥顶替与激活位维护复用插件生命周期逻辑；
 * 主题随附页面集随启用导入发布、随停用自动下线转草稿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeCenterAppService {

    private static final String CMS_CAPABILITY_CODE = "cms";
    private static final String SCOPE_SITE = "SITE";
    private static final String BUILTIN_THEME_CODE = "default";

    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final PluginModuleRepo pluginModuleRepo;
    private final PluginAppService pluginAppService;
    private final PluginThemeAppService pluginThemeAppService;
    private final CmsPresetAppService cmsPresetAppService;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectMapper objectMapper;

    /**
     * 主题中心总览：内置默认主题卡 + 全部声明了 SITE 作用域的主题插件卡
     * （含已安装未启用，来源于启用时持久化的主题作用域），附首页方案列表。
     */
    @Transactional(readOnly = true)
    public ThemeCenterOverviewDTO overview() {
        PluginThemeDTO activeTheme = pluginThemeAppService.activeThemes().get(SCOPE_SITE);
        String activeSite = activeTheme == null ? null : activeTheme.getPluginCode();
        List<ThemeCenterThemeCardDTO> cards = new ArrayList<>();
        cards.add(ThemeCenterThemeCardDTO.builder()
                .pluginCode(null)
                .code(BUILTIN_THEME_CODE)
                .name("内置默认主题")
                .description("宿主自带的默认公开站主题，未启用任何主题插件时的回落主题")
                .hasHomePreset(false)
                .hasPageSet(false)
                .active(activeSite == null)
                .enabled(true)
                .build());
        Map<String, PluginThemeInfo> runtimeThemes = new LinkedHashMap<>();
        for (PluginThemeInfo theme : pluginRuntimeGateway.themes()) {
            if (theme.scopes().contains(SCOPE_SITE)) {
                runtimeThemes.put(theme.pluginCode(), theme);
            }
        }
        Set<String> covered = new LinkedHashSet<>();
        for (PluginModule module : pluginModuleRepo.findAll()) {
            if (module.getThemeScopes() == null || !module.getThemeScopes().contains(SCOPE_SITE)) {
                continue;
            }
            covered.add(module.getCode());
            PluginThemeInfo runtime = runtimeThemes.get(module.getCode());
            cards.add(runtime != null ? runtimeCard(runtime, activeSite) : offlineCard(module, activeSite));
        }
        // 兼容旧数据：已启用主题但尚未持久化 themeScopes 的插件（本次升级前启用的）
        for (Map.Entry<String, PluginThemeInfo> entry : runtimeThemes.entrySet()) {
            if (!covered.contains(entry.getKey())) {
                cards.add(runtimeCard(entry.getValue(), activeSite));
            }
        }
        boolean cmsEnabled = capabilityEnabled();
        List<HomePagePresetDTO> presets = cmsEnabled ? cmsPresetAppService.list() : List.of();
        return ThemeCenterOverviewDTO.builder()
                .themes(cards)
                .presets(presets)
                .cmsEnabled(cmsEnabled)
                .build();
    }

    /**
     * 切换公开站主题：启用目标主题插件（同作用域主题插件自动顶替互斥），
     * 当前首页定制自动快照、目标主题自带方案与页面集自动导入发布。
     */
    @Transactional
    public void activateSiteTheme(String pluginCode) {
        PluginModule module = pluginModuleRepo.findByCode(pluginCode)
                .orElseThrow(() -> new BizException("插件不存在，请先刷新插件目录"));
        boolean declaresSite = pluginRuntimeGateway.theme(pluginCode)
                .map(theme -> theme.scopes().contains(SCOPE_SITE))
                .orElse(module.getThemeScopes() != null && module.getThemeScopes().contains(SCOPE_SITE));
        if (!declaresSite) {
            throw new BizException("该插件未声明公开站主题");
        }
        pluginAppService.enable(pluginCode);
    }

    /**
     * 恢复内置默认主题：禁用当前占据 SITE 激活位的主题插件，
     * 其随附页面自动下线转草稿，首页定制保留（可从快照/方案切回）。
     */
    @Transactional
    public void deactivateSiteTheme() {
        PluginThemeDTO active = pluginThemeAppService.activeThemes().get(SCOPE_SITE);
        if (active == null) {
            throw new BizException("当前未启用公开站主题");
        }
        pluginAppService.disable(active.getPluginCode());
    }

    private ThemeCenterThemeCardDTO runtimeCard(PluginThemeInfo theme, String activeSite) {
        return ThemeCenterThemeCardDTO.builder()
                .pluginCode(theme.pluginCode())
                .code(theme.code())
                .name(theme.name())
                .description(theme.description())
                .preview(theme.preview())
                .assetRevision(theme.assetRevision())
                .hasHomePreset(StringUtils.hasText(theme.homePreset()))
                .hasPageSet(declaresPageSet(theme))
                .active(theme.pluginCode().equals(activeSite))
                .enabled(true)
                .build();
    }

    private ThemeCenterThemeCardDTO offlineCard(PluginModule module, String activeSite) {
        return ThemeCenterThemeCardDTO.builder()
                .pluginCode(module.getCode())
                .code(module.getCode())
                .name(module.getName())
                .description(module.getDescription())
                .active(module.getCode().equals(activeSite))
                .enabled(false)
                .build();
    }

    private boolean declaresPageSet(PluginThemeInfo theme) {
        if (!StringUtils.hasText(theme.homePreset())) {
            return false;
        }
        try {
            return pluginRuntimeGateway.frontendAsset(theme.pluginCode(), theme.homePreset())
                    .map(asset -> {
                        try {
                            PluginHomePresetPayload payload = objectMapper.readValue(
                                    new String(asset.body(), StandardCharsets.UTF_8), PluginHomePresetPayload.class);
                            return payload.getPages() != null && !payload.getPages().isEmpty();
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .orElse(false);
        } catch (Exception e) {
            log.debug("读取主题页面集声明失败：plugin={}, reason={}", theme.pluginCode(), e.getMessage());
            return false;
        }
    }

    private boolean capabilityEnabled() {
        return capabilityModuleRepo.findByCode(CMS_CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
    }
}
