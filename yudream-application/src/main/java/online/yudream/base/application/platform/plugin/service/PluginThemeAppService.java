package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.cms.service.CmsPresetAppService;
import online.yudream.base.application.platform.plugin.assembler.PluginAssembler;
import online.yudream.base.application.platform.plugin.dto.PluginThemeDTO;
import online.yudream.base.application.platform.plugin.dto.PluginThemeOverviewDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginThemeInfo;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 插件主题编排：维护每个 scope（SITE/ADMIN）当前激活的主题插件，
 * 并配合插件生命周期实现同 scope 主题插件的自动顶替互斥。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginThemeAppService {

    private static final String CATEGORY_PLUGIN_THEME = "plugin-theme";
    private static final String KEY_ACTIVE_PREFIX = "pluginTheme.active.";

    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final SettingRepo settingRepo;
    private final CmsPresetAppService cmsPresetAppService;

    /**
     * 公开站/后台当前激活的主题（scope -> 主题），未激活的 scope 不出现在结果中。
     * 激活位指向的插件已不提供该 scope 主题时按未激活处理。
     */
    @Transactional(readOnly = true)
    public Map<String, PluginThemeDTO> activeThemes() {
        Map<String, PluginThemeDTO> result = new LinkedHashMap<>();
        for (String scope : List.of("SITE", "ADMIN")) {
            resolveActive(scope).ifPresent(theme -> result.put(scope, theme));
        }
        return result;
    }

    /**
     * 管理视图：全部已启用插件声明的主题 + 每个 scope 的激活插件编码。
     */
    @Transactional(readOnly = true)
    public PluginThemeOverviewDTO overview() {
        Map<String, String> active = new LinkedHashMap<>();
        for (String scope : List.of("SITE", "ADMIN")) {
            resolveActive(scope).ifPresent(theme -> active.put(scope, theme.getPluginCode()));
        }
        return PluginThemeOverviewDTO.builder()
                .themes(pluginRuntimeGateway.themes().stream().map(PluginAssembler::toThemeDTO).toList())
                .active(active)
                .build();
    }

    /**
     * 启用插件后计算同 scope 冲突的主题插件（已启用且提供了相交 scope 主题的其他插件）。
     */
    public List<String> themeConflicts(String pluginCode) {
        Optional<PluginThemeInfo> theme = pluginRuntimeGateway.theme(pluginCode);
        if (theme.isEmpty()) {
            return List.of();
        }
        List<PluginThemeInfo> enabledThemes = pluginRuntimeGateway.themes();
        List<String> conflicts = new ArrayList<>();
        for (String scope : theme.get().scopes()) {
            for (PluginThemeInfo other : enabledThemes) {
                if (!other.pluginCode().equals(pluginCode) && other.scopes().contains(scope)
                        && !conflicts.contains(other.pluginCode())) {
                    conflicts.add(other.pluginCode());
                }
            }
        }
        return conflicts;
    }

    /**
     * 把插件声明的主题写入其每个 scope 的激活位（插件已启用且注册了主题时调用）。
     * SITE 主题声明了首页方案时，顺带导入并应用（当前定制自动快照，可在内容定制
     * 的方案列表一键切回）；方案导入失败不阻塞主题激活。
     */
    @Transactional
    public void activate(String pluginCode) {
        Optional<PluginThemeInfo> theme = pluginRuntimeGateway.theme(pluginCode);
        if (theme.isEmpty()) {
            return;
        }
        for (String scope : theme.get().scopes()) {
            saveActive(scope, pluginCode);
        }
        importHomePreset(theme.get());
    }

    private void importHomePreset(PluginThemeInfo theme) {
        if (!theme.scopes().contains("SITE") || !StringUtils.hasText(theme.homePreset())) {
            return;
        }
        try {
            String presetJson = pluginRuntimeGateway.frontendAsset(theme.pluginCode(), theme.homePreset())
                    .map(asset -> new String(asset.body(), StandardCharsets.UTF_8))
                    .orElseThrow(() -> new BizException("主题首页方案资产不存在"));
            cmsPresetAppService.importPluginPreset(theme.pluginCode(), theme.name(), presetJson);
        } catch (Exception e) {
            log.warn("导入插件主题首页方案失败，仅应用主题样式：plugin={}, reason={}", theme.pluginCode(), e.getMessage());
        }
    }

    /**
     * 禁用/卸载/删除插件时清除其占据的激活位，该 scope 回落宿主内置主题。
     */
    @Transactional
    public void clearActivation(String pluginCode) {
        for (String scope : List.of("SITE", "ADMIN")) {
            if (pluginCode.equals(readActive(scope).orElse(null))) {
                saveActive(scope, "");
            }
        }
    }

    /**
     * 启动恢复后校正激活位：激活位失效时清除；scope 无激活且恰好有一个已启用
     * 主题插件提供该 scope 时补写激活位。
     */
    @Transactional
    public void reconcileAfterRestore() {
        List<PluginThemeInfo> enabledThemes = pluginRuntimeGateway.themes();
        for (String scope : List.of("SITE", "ADMIN")) {
            String active = readActive(scope).orElse(null);
            String recorded = active;
            boolean activeValid = recorded != null && enabledThemes.stream()
                    .anyMatch(theme -> theme.pluginCode().equals(recorded) && theme.scopes().contains(scope));
            if (active != null && !activeValid) {
                log.warn("插件主题激活位失效，已清除：scope={}, plugin={}", scope, active);
                saveActive(scope, "");
                active = null;
            }
            if (active == null) {
                List<PluginThemeInfo> candidates = enabledThemes.stream()
                        .filter(theme -> theme.scopes().contains(scope))
                        .toList();
                if (candidates.size() == 1) {
                    saveActive(scope, candidates.getFirst().pluginCode());
                } else if (candidates.size() > 1) {
                    log.warn("scope {} 存在多个已启用主题插件但未记录激活位，回落宿主内置主题：{}", scope,
                            candidates.stream().map(PluginThemeInfo::pluginCode).toList());
                }
            }
        }
    }

    private Optional<PluginThemeDTO> resolveActive(String scope) {
        return readActive(scope)
                .flatMap(pluginRuntimeGateway::theme)
                .filter(theme -> theme.scopes().contains(scope))
                .map(PluginAssembler::toThemeDTO);
    }

    private Optional<String> readActive(String scope) {
        return settingRepo.findByKey(activeKey(scope))
                .map(Setting::getValue)
                .filter(StringUtils::hasText);
    }

    private void saveActive(String scope, String pluginCode) {
        String key = activeKey(scope);
        Setting setting = settingRepo.findByKey(key).orElseGet(() -> Setting.builder()
                .key(key)
                .build());
        setting.setValue(pluginCode);
        setting.setType(SettingType.STRING);
        setting.setCategory(CATEGORY_PLUGIN_THEME);
        setting.setDescription("插件主题激活位：" + scope);
        settingRepo.save(setting);
    }

    private String activeKey(String scope) {
        return KEY_ACTIVE_PREFIX + scope.toLowerCase(Locale.ROOT);
    }
}
