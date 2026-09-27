package online.yudream.base.application.platform.mobile.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mobile.dto.MobileSiteInfoDTO;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.mobile.valobj.MobileCapabilityConfig;
import online.yudream.base.domain.system.about.service.AboutBuildInfoGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public MobileSiteInfoDTO siteInfo() {
        var site = settingAppService.siteSettings();
        return MobileSiteInfoDTO.builder()
                .siteName(site.getSiteName())
                .logo(site.getLogo())
                .favicon(site.getFavicon())
                .version(aboutBuildInfoGateway.read().map(info -> info.version() == null ? "" : info.version()).orElse(""))
                .mobileEnabled(mobileEnabled())
                .build();
    }

    private boolean mobileEnabled() {
        return capabilityModuleRepo.findByCode(MobileCapabilityConfig.CAPABILITY_CODE)
                .filter(CapabilityModule::enabled)
                .isPresent();
    }
}
