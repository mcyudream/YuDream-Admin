package online.yudream.base.interfaces.system.setting.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.setting.assembler.SettingWebAssembler;
import online.yudream.base.interfaces.system.setting.request.SiteSettingUpdateRequest;
import online.yudream.base.interfaces.system.setting.request.ThemeSettingUpdateRequest;
import online.yudream.base.interfaces.system.setting.res.SiteSettingRes;
import online.yudream.base.interfaces.system.setting.res.ThemeSettingRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/system/settings")
@RequiredArgsConstructor
public class SystemSettingController {

    private final SettingAppService settingAppService;

    @GetMapping("/site")
    @PermissionRegister(code = "system:setting:view", name = "查看系统设置", module = "系统管理", desc = "查看站点系统设置")
    public Result<SiteSettingRes> siteSettings() {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.siteSettings()));
    }

    @PutMapping("/site")
    @PermissionRegister(code = "system:setting:edit", name = "编辑系统设置", module = "系统管理", desc = "编辑站点系统设置")
    public Result<SiteSettingRes> updateSiteSettings(@Valid @RequestBody SiteSettingUpdateRequest request) {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.updateSiteSettings(SettingWebAssembler.toCmd(request))));
    }

    @GetMapping("/theme")
    @PermissionRegister(code = "system:setting:theme:view", name = "查看主题配置", module = "系统管理", desc = "查看前端主题配置")
    public Result<ThemeSettingRes> themeSettings() {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.themeSettings()));
    }

    @PutMapping("/theme")
    @PermissionRegister(code = "system:setting:theme:edit", name = "编辑主题配置", module = "系统管理", desc = "编辑前端主题配置")
    public Result<ThemeSettingRes> updateThemeSettings(@Valid @RequestBody ThemeSettingUpdateRequest request) {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.updateThemeSettings(SettingWebAssembler.toCmd(request))));
    }

    @PostMapping("/site/logo")
    @PermissionRegister(code = "system:setting:upload", name = "上传系统资源", module = "系统管理", desc = "上传站点 Logo 和图标")
    public Result<SiteSettingRes> uploadLogo(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.uploadLogo(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                StpUtil.getLoginIdAsLong())));
    }

    @PostMapping("/site/favicon")
    @PermissionRegister(code = "system:setting:upload", name = "上传系统资源", module = "系统管理", desc = "上传站点 Logo 和图标")
    public Result<SiteSettingRes> uploadFavicon(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(SettingWebAssembler.toRes(settingAppService.uploadFavicon(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                StpUtil.getLoginIdAsLong())));
    }
}
