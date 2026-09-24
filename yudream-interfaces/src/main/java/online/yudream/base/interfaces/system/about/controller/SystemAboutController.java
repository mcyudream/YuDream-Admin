package online.yudream.base.interfaces.system.about.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.about.service.AboutAppService;
import online.yudream.base.application.system.about.service.AboutVersionCheckAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.about.assembler.AboutWebAssembler;
import online.yudream.base.interfaces.system.about.res.AboutLatestRes;
import online.yudream.base.interfaces.system.about.res.AboutOverviewRes;
import online.yudream.base.interfaces.system.about.res.PluginGraphRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/about")
@RequiredArgsConstructor
public class SystemAboutController {

    private final AboutAppService aboutAppService;
    private final AboutVersionCheckAppService aboutVersionCheckAppService;

    @GetMapping
    @PermissionRegister(code = "system:about:view", name = "查看关于系统", module = "系统管理", desc = "查看框架/SPI 当前版本与插件装载统计")
    public Result<AboutOverviewRes> overview() {
        return Result.ok(AboutWebAssembler.toRes(aboutAppService.overview()));
    }

    @GetMapping("/latest")
    @PermissionRegister(code = "system:about:view", name = "查看关于系统", module = "系统管理", desc = "探测契约包在发布仓库中的最新版本")
    public Result<AboutLatestRes> latest() {
        return Result.ok(AboutWebAssembler.toRes(aboutVersionCheckAppService.latest()));
    }

    @GetMapping("/plugin-graph")
    @PermissionRegister(code = "system:about:view", name = "查看关于系统", module = "系统管理", desc = "查看插件依赖与装载状态图")
    public Result<PluginGraphRes> pluginGraph() {
        return Result.ok(AboutWebAssembler.toRes(aboutAppService.pluginGraph()));
    }
}
