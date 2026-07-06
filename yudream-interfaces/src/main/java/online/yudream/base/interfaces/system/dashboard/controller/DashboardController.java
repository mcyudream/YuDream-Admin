package online.yudream.base.interfaces.system.dashboard.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.dashboard.service.DashboardAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.dashboard.assembler.DashboardWebAssembler;
import online.yudream.base.interfaces.system.dashboard.request.DashboardLayoutSaveRequest;
import online.yudream.base.interfaces.system.dashboard.res.DashboardCardRes;
import online.yudream.base.interfaces.system.dashboard.res.DashboardLayoutRes;
import online.yudream.base.interfaces.system.dashboard.res.DashboardWorkspaceRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardAppService dashboardAppService;

    @GetMapping("/me")
    public Result<DashboardWorkspaceRes> workspace() {
        return Result.ok(DashboardWebAssembler.toRes(dashboardAppService.workspace(StpUtil.getLoginIdAsLong())));
    }

    @PutMapping("/me/layout")
    public Result<DashboardLayoutRes> saveMyLayout(@Valid @RequestBody DashboardLayoutSaveRequest request) {
        return Result.ok(DashboardWebAssembler.toRes(
                dashboardAppService.saveUserLayout(StpUtil.getLoginIdAsLong(), DashboardWebAssembler.toCmd(request))));
    }

    @DeleteMapping("/me/layout")
    public Result<Void> resetMyLayout() {
        dashboardAppService.resetUserLayout(StpUtil.getLoginIdAsLong());
        return Result.ok();
    }

    @GetMapping("/cards")
    @PermissionRegister(code = "system:dashboard:config", name = "查看首页卡片", module = "系统管理", desc = "查看所有可配置首页卡片")
    public Result<java.util.List<DashboardCardRes>> cards() {
        return Result.ok(dashboardAppService.cards().stream().map(DashboardWebAssembler::toRes).toList());
    }

    @GetMapping("/default-layout")
    @PermissionRegister(code = "system:dashboard:config", name = "查看默认首页布局", module = "系统管理", desc = "查看系统默认用户首页卡片布局")
    public Result<DashboardLayoutRes> defaultLayout() {
        return Result.ok(DashboardWebAssembler.toRes(dashboardAppService.defaultLayout()));
    }

    @PutMapping("/default-layout")
    @PermissionRegister(code = "system:dashboard:config", name = "配置默认首页布局", module = "系统管理", desc = "配置系统默认用户首页卡片布局")
    public Result<DashboardLayoutRes> saveDefaultLayout(@Valid @RequestBody DashboardLayoutSaveRequest request) {
        return Result.ok(DashboardWebAssembler.toRes(dashboardAppService.saveDefaultLayout(DashboardWebAssembler.toCmd(request))));
    }
}
