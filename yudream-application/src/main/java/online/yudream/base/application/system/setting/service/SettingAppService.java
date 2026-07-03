package online.yudream.base.application.system.setting.service;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.application.system.setting.cmd.SiteSettingUpdateCmd;
import online.yudream.base.application.system.setting.cmd.ThemeSettingUpdateCmd;
import online.yudream.base.application.system.setting.dto.SiteSettingDTO;
import online.yudream.base.application.system.setting.dto.ThemeSettingDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置查询应用服务。
 */
@Service
@RequiredArgsConstructor
public class SettingAppService {

    private final SettingRepo settingRepo;
    private final FileAppService fileAppService;
    private final ObjectMapper objectMapper;

    private static final String CATEGORY_SITE = "site";
    private static final String CATEGORY_THEME = "theme";
    private static final String KEY_SITE_NAME = "siteName";
    private static final String KEY_SITE_DESCRIPTION = "siteDescription";
    private static final String KEY_LOGO = "logo";
    private static final String KEY_FAVICON = "favicon";
    private static final String KEY_COPYRIGHT_COMPANY = "copyrightCompany";
    private static final String KEY_COPYRIGHT_WEBSITE = "copyrightWebsite";
    private static final String KEY_COPYRIGHT_DATES = "copyrightDates";
    private static final String KEY_THEME_CONFIG = "appSettings";

    @Transactional(readOnly = true)
    public Map<String, String> publicSettings() {
        List<Setting> settings = settingRepo.findByCategory(CATEGORY_SITE);
        Map<String, String> map = new HashMap<>();
        for (Setting setting : settings) {
            if (setting == null || !StringUtils.hasText(setting.getKey())) {
                continue;
            }
            map.put(setting.getKey(), setting.getValue());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public SiteSettingDTO siteSettings() {
        Map<String, String> settings = publicSettings();
        return toSiteDTO(settings);
    }

    @Transactional(readOnly = true)
    public ThemeSettingDTO themeSettings() {
        return ThemeSettingDTO.builder()
                .config(readThemeConfig())
                .build();
    }

    @Transactional
    public SiteSettingDTO updateSiteSettings(SiteSettingUpdateCmd cmd) {
        if (!StringUtils.hasText(cmd.getSiteName())) {
            throw new BizException("站点名称不能为空");
        }
        save(KEY_SITE_NAME, cmd.getSiteName(), "站点名称");
        save(KEY_SITE_DESCRIPTION, cmd.getSiteDescription(), "站点描述");
        save(KEY_COPYRIGHT_COMPANY, cmd.getCopyrightCompany(), "版权公司");
        save(KEY_COPYRIGHT_WEBSITE, cmd.getCopyrightWebsite(), "版权网站");
        save(KEY_COPYRIGHT_DATES, cmd.getCopyrightDates(), "版权年份");
        return siteSettings();
    }

    @Transactional
    public ThemeSettingDTO updateThemeSettings(ThemeSettingUpdateCmd cmd) {
        Map<String, Object> config = cmd.getConfig() == null ? Map.of() : cmd.getConfig();
        save(KEY_THEME_CONFIG, writeJson(config), "前端主题配置", CATEGORY_THEME, SettingType.JSON);
        return ThemeSettingDTO.builder()
                .config(config)
                .build();
    }

    @Transactional
    public SiteSettingDTO uploadLogo(InputStream inputStream, String originalName, String contentType, long size, Long userId) {
        String url = uploadImageSetting(KEY_LOGO, "系统 Logo", inputStream, originalName, contentType, size, userId);
        Map<String, String> settings = publicSettings();
        settings.put(KEY_LOGO, url);
        return toSiteDTO(settings);
    }

    @Transactional
    public SiteSettingDTO uploadFavicon(InputStream inputStream, String originalName, String contentType, long size, Long userId) {
        String url = uploadImageSetting(KEY_FAVICON, "站点图标", inputStream, originalName, contentType, size, userId);
        Map<String, String> settings = publicSettings();
        settings.put(KEY_FAVICON, url);
        return toSiteDTO(settings);
    }

    private String uploadImageSetting(String key, String description, InputStream inputStream, String originalName,
                                      String contentType, long size, Long userId) {
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase().startsWith("image/")) {
            throw new BizException(description + "只支持图片文件");
        }
        FileObjectDTO file = fileAppService.upload(inputStream, originalName, contentType, size, "site", userId, true);
        save(key, file.getUrl(), description);
        return file.getUrl();
    }

    private void save(String key, String value, String description) {
        save(key, value, description, CATEGORY_SITE, SettingType.STRING);
    }

    private void save(String key, String value, String description, String category, SettingType type) {
        Setting setting = settingRepo.findByKey(key).orElseGet(() -> Setting.builder()
                .key(key)
                .type(type)
                .category(category)
                .description(description)
                .build());
        setting.setValue(value);
        setting.setType(type);
        setting.setCategory(category);
        setting.setDescription(description);
        settingRepo.save(setting);
    }

    private Map<String, Object> readThemeConfig() {
        return settingRepo.findByKey(KEY_THEME_CONFIG)
                .map(Setting::getValue)
                .filter(StringUtils::hasText)
                .map(this::readJson)
                .orElseGet(Map::of);
    }

    private Map<String, Object> readJson(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        }
        catch (Exception e) {
            throw new BizException("主题配置解析失败");
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception e) {
            throw new BizException("主题配置保存失败");
        }
    }

    private SiteSettingDTO toSiteDTO(Map<String, String> settings) {
        return SiteSettingDTO.builder()
                .siteName(settings.getOrDefault(KEY_SITE_NAME, "YuDreamAdmin"))
                .siteDescription(settings.get(KEY_SITE_DESCRIPTION))
                .logo(settings.get(KEY_LOGO))
                .favicon(settings.get(KEY_FAVICON))
                .copyrightCompany(settings.get(KEY_COPYRIGHT_COMPANY))
                .copyrightWebsite(settings.get(KEY_COPYRIGHT_WEBSITE))
                .copyrightDates(settings.get(KEY_COPYRIGHT_DATES))
                .build();
    }
}
