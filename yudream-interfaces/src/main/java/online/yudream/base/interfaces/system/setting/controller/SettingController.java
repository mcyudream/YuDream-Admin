package online.yudream.base.interfaces.system.setting.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.setting.assembler.SettingWebAssembler;
import online.yudream.base.interfaces.system.setting.res.SiteSettingRes;
import online.yudream.base.interfaces.system.setting.res.ThemeSettingRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置公开接口。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingAppService settingAppService;

    @GetMapping("/public")
    public Result<SiteSettingRes> publicSettings() {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.siteSettings()));
    }

    @GetMapping("/theme")
    public Result<ThemeSettingRes> themeSettings() {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.themeSettings()));
    }
}
