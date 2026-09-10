package online.yudream.base.application.platform.theme.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.cms.aggregate.HomePageLayout;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 公开站当前激活主题的轻量查询：直读插件主题激活位 Setting，
 * 供 CMS 等应用服务在不依赖插件主题编排（避免循环依赖）的情况下解析「当前主题」。
 */
@Service
@RequiredArgsConstructor
public class SiteThemeQueryService {

    /** SITE 作用域主题激活位 Setting 键（与 PluginThemeAppService 维护的键一致）。 */
    private static final String KEY_ACTIVE_SITE = "pluginTheme.active.site";

    private final SettingRepo settingRepo;

    /**
     * 当前激活的公开站主题编码；未激活任何插件主题时返回内置默认主题。
     */
    @Transactional(readOnly = true)
    public String activeSiteThemeCode() {
        return settingRepo.findByKey(KEY_ACTIVE_SITE)
                .map(Setting::getValue)
                .filter(StringUtils::hasText)
                .orElse(HomePageLayout.DEFAULT_THEME_CODE);
    }
}
