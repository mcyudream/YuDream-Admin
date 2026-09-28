package online.yudream.base.interfaces.platform.mobile.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.mobile.dto.MobileSiteInfoDTO;
import online.yudream.base.application.platform.mobile.service.MobilePublicAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.mobile.assembler.MobileWebAssembler;
import online.yudream.base.interfaces.platform.mobile.res.MobileSiteInfoRes;
import online.yudream.base.interfaces.platform.mobile.res.MobileThemeActiveRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 匿名站点发现端点：移动 App 添加域时探测"这是一个 YuDream 站点"。
 * <p>
 * 刻意独立于 {@link MobileController}：本端点不做登录/权限检查，也不受
 * mobile-app 能力项目闸门约束（闸门关闭时 MobileController 整体不注册，
 * 而域添加探测仍需识别站点身份，仅以 mobileEnabled 如实反映能力状态）。
 */
@RestController
@RequestMapping("/api/mobile/public")
@RequiredArgsConstructor
public class MobilePublicController {

    private final MobilePublicAppService mobilePublicAppService;

    @GetMapping("/site-info")
    public Result<MobileSiteInfoRes> siteInfo() {
        MobileSiteInfoDTO info = mobilePublicAppService.siteInfo();
        return Result.ok(MobileWebAssembler.toSiteInfoRes(info));
    }

    /** 激活站点主题与域主题色（匿名：登录前也要按站点主题渲染）。 */
    @GetMapping("/theme/active")
    public Result<MobileThemeActiveRes> themeActive() {
        return Result.ok(MobileWebAssembler.toThemeActiveRes(mobilePublicAppService.themeActive()));
    }
}
