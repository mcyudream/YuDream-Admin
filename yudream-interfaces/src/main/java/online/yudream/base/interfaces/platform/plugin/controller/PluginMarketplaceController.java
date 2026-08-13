package online.yudream.base.interfaces.platform.plugin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginStoreAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketplaceInstallRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketplaceUpdateRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceUpdateRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceUpdatePlanRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketplaceUpdateResultRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginModuleRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginDetailRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginStorePluginRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/plugin-marketplace")
@RequiredArgsConstructor
public class PluginMarketplaceController {

    private final PluginStoreAppService pluginStoreAppService;

    @GetMapping
    @PermissionRegister(code = "platform:plugin-marketplace:view", name = "查看插件市场", module = "平台插件市场", desc = "查看在线插件市场目录")
    public Result<List<PluginStorePluginRes>> list() {
        return Result.ok(PluginWebAssembler.toStoreResList(pluginStoreAppService.list()));
    }

    @GetMapping("/updates")
    @PermissionRegister(code = "platform:plugin-marketplace:view", name = "查看插件更新", module = "平台插件市场", desc = "检查已安装插件的市场更新，不执行下载或安装")
    public Result<List<PluginMarketplaceUpdateRes>> updates() {
        return Result.ok(PluginWebAssembler.toUpdateResList(pluginStoreAppService.updates()));
    }

    @GetMapping("/update-plan")
    @PermissionRegister(code = "platform:plugin-marketplace:view", name = "查看插件更新计划", module = "平台插件市场", desc = "预览已安装插件更新影响，不执行下载或安装")
    public Result<List<PluginMarketplaceUpdatePlanRes>> updatePlans() {
        return Result.ok(PluginWebAssembler.toUpdatePlanResList(pluginStoreAppService.updatePlans()));
    }

    @GetMapping("/{code}/update-plan")
    @PermissionRegister(code = "platform:plugin-marketplace:view", name = "查看插件更新计划", module = "平台插件市场", desc = "预览指定插件版本更新影响，不执行下载或安装")
    public Result<PluginMarketplaceUpdatePlanRes> updatePlan(@PathVariable String code,
                                                               @org.springframework.web.bind.annotation.RequestParam(required = false) String targetVersion) {
        return Result.ok(PluginWebAssembler.toUpdatePlanRes(pluginStoreAppService.updatePlan(code, targetVersion)));
    }

    @GetMapping("/{code}")
    @PermissionRegister(code = "platform:plugin-marketplace:view", name = "查看插件市场", module = "平台插件市场", desc = "查看在线插件市场详情")
    public Result<PluginStorePluginDetailRes> detail(@PathVariable String code) {
        return Result.ok(PluginWebAssembler.toStoreDetailRes(pluginStoreAppService.detail(code)));
    }

    @PostMapping("/{code}/update")
    @PermissionRegister(code = "platform:plugin:manage", name = "确认插件更新", module = "平台插件市场", desc = "确认指定版本的插件更新；请在受控停机后重启，仅恢复更新前已启用的插件")
    public Result<PluginMarketplaceUpdateResultRes> update(@PathVariable String code,
                                                             @Valid @RequestBody PluginMarketplaceUpdateRequest request) {
        return Result.ok(PluginWebAssembler.toUpdateResultRes(pluginStoreAppService.update(code, request.getReleaseVersion())));
    }

    @PostMapping("/{code}/rollback")
    @PermissionRegister(code = "platform:plugin:manage", name = "确认插件回滚", module = "平台插件市场", desc = "确认回滚到本地备份版本，回滚后需重启且不自动启用")
    public Result<PluginMarketplaceUpdateResultRes> rollback(@PathVariable String code) {
        return Result.ok(PluginWebAssembler.toUpdateResultRes(pluginStoreAppService.rollback(code)));
    }

    @PostMapping("/{code}/install")
    @PermissionRegister(code = "platform:plugin:manage", name = "安装市场插件", module = "平台插件市场", desc = "安装指定版本的在线插件，不自动启用")
    public Result<List<PluginModuleRes>> install(@PathVariable String code,
                                                  @Valid @RequestBody PluginMarketplaceInstallRequest request) {
        return Result.ok(PluginWebAssembler.toResList(pluginStoreAppService.install(code, request.getReleaseVersion())));
    }
}
