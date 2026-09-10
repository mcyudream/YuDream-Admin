package online.yudream.base.interfaces.platform.theme.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.theme.service.ThemeCenterAppService;
import online.yudream.base.application.platform.theme.service.ThemeConfigAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.theme.assembler.ThemeCenterWebAssembler;
import online.yudream.base.interfaces.platform.theme.request.ThemeConfigSaveRequest;
import online.yudream.base.interfaces.platform.theme.res.ThemeCenterOverviewRes;
import online.yudream.base.interfaces.platform.theme.res.ThemeConfigRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/themes")
@RequiredArgsConstructor
public class ThemeCenterController {

    private final ThemeCenterAppService themeCenterAppService;
    private final ThemeConfigAppService themeConfigAppService;

    @GetMapping("/overview")
    @PermissionRegister(code = "platform:theme-center:view", name = "查看主题中心", module = "平台能力", desc = "查看主题中心主题与首页方案总览")
    public Result<ThemeCenterOverviewRes> overview() {
        return Result.ok(ThemeCenterWebAssembler.toRes(themeCenterAppService.overview()));
    }

    @PostMapping("/{code}/activate")
    @PermissionRegister(code = "platform:theme-center:use", name = "切换公开站主题", module = "平台能力", desc = "启用主题插件作为公开站主题")
    public Result<Void> activate(@PathVariable String code) {
        themeCenterAppService.activateSiteTheme(code);
        return Result.ok();
    }

    @PostMapping("/deactivate")
    @PermissionRegister(code = "platform:theme-center:use", name = "恢复默认主题", module = "平台能力", desc = "停用当前公开站主题插件并回落内置默认主题")
    public Result<Void> deactivate() {
        themeCenterAppService.deactivateSiteTheme();
        return Result.ok();
    }

    @GetMapping("/{code}/config")
    @PermissionRegister(code = "platform:theme-center:config", name = "配置主题", module = "平台能力", desc = "查看主题配置 schema 与当前配置值")
    public Result<ThemeConfigRes> config(@PathVariable String code) {
        return Result.ok(ThemeCenterWebAssembler.toRes(themeConfigAppService.config(code)));
    }

    @PutMapping("/{code}/config")
    @PermissionRegister(code = "platform:theme-center:config", name = "配置主题", module = "平台能力", desc = "按主题 schema 保存主题配置，公开站即时生效")
    public Result<ThemeConfigRes> saveConfig(@PathVariable String code, @RequestBody ThemeConfigSaveRequest request) {
        return Result.ok(ThemeCenterWebAssembler.toRes(
                themeConfigAppService.save(ThemeCenterWebAssembler.toSaveCmd(code, request))));
    }
}

