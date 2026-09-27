package online.yudream.base.application.platform.mobile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mobile.dto.MobileBannerDTO;
import online.yudream.base.application.platform.mobile.dto.MobileSiteInfoDTO;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.mobile.valobj.MobileCapabilityConfig;
import online.yudream.base.domain.system.about.service.AboutBuildInfoGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 匿名站点发现查询：供移动 App 添加域时探测站点身份。
 * <p>
 * 刻意不走 mobile-app 能力双闸门——能力关闭时域添加探测仍需识别"这是一个
 * YuDream 站点"，仅以 mobileEnabled 如实反映应用闸门状态。
 */
@Service
@RequiredArgsConstructor
public class MobilePublicAppService {

    private final SettingAppService settingAppService;
    private final AboutBuildInfoGateway aboutBuildInfoGateway;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public MobileSiteInfoDTO siteInfo() {
        var site = settingAppService.siteSettings();
        var branding = branding();
        return MobileSiteInfoDTO.builder()
                .siteName(site.getSiteName())
                .logo(site.getLogo())
                .favicon(site.getFavicon())
                .version(aboutBuildInfoGateway.read().map(info -> info.version() == null ? "" : info.version()).orElse(""))
                .mobileEnabled(mobileEnabled())
                .loginHeroImage(branding.getLoginHeroImage())
                .loginHeroBackground(branding.getLoginHeroBackground())
                .homeBanners(branding.getHomeBanners())
                .build();
    }

    private boolean mobileEnabled() {
        return capabilityModuleRepo.findByCode(MobileCapabilityConfig.CAPABILITY_CODE)
                .filter(CapabilityModule::enabled)
                .isPresent();
    }

    /** 品牌与首页定制：仅能力启用时读取配置；能力关闭/键缺失一律回落空值（App 回退内置样式）。 */
    private MobileSiteInfoDTO branding() {
        return capabilityModuleRepo.findByCode(MobileCapabilityConfig.CAPABILITY_CODE)
                .filter(CapabilityModule::enabled)
                .map(module -> MobileSiteInfoDTO.builder()
                        .loginHeroImage(MobileCapabilityConfig.textConfig(module.getConfig(), MobileCapabilityConfig.KEY_LOGIN_HERO_IMAGE))
                        .loginHeroBackground(MobileCapabilityConfig.textConfig(module.getConfig(), MobileCapabilityConfig.KEY_LOGIN_HERO_BACKGROUND))
                        .homeBanners(banners(module.getConfig()))
                        .build())
                .orElseGet(() -> MobileSiteInfoDTO.builder().build());
    }

    /** homeBanners 配置为 JSON 数组字符串；解析失败/为空一律空列表，不让坏配置打挂发现接口。 */
    private List<MobileBannerDTO> banners(Map<String, String> config) {
        String raw = MobileCapabilityConfig.textConfig(config, MobileCapabilityConfig.KEY_HOME_BANNERS);
        if (raw.isEmpty()) {
            return List.of();
        }
        try {
            List<MobileBannerDTO> banners = objectMapper.readValue(
                    raw, new TypeReference<List<MobileBannerDTO>>() {});
            return banners == null ? List.of() : banners;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
