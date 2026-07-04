package online.yudream.base.interfaces.platform.plugin.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendManifestRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginModuleRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/plugins")
@RequiredArgsConstructor
public class PluginController {

    private final PluginAppService pluginAppService;

    @GetMapping
    @PermissionRegister(code = "platform:plugin:view", name = "查看插件管理", module = "平台插件", desc = "查看插件列表与运行状态")
    public Result<List<PluginModuleRes>> list() {
        return Result.ok(PluginWebAssembler.toResList(pluginAppService.list()));
    }

    @PostMapping("/refresh")
    @PermissionRegister(code = "platform:plugin:manage", name = "刷新插件目录", module = "平台插件", desc = "扫描插件目录并同步插件描述")
    public Result<List<PluginModuleRes>> refresh() {
        return Result.ok(PluginWebAssembler.toResList(pluginAppService.refresh()));
    }

    @PostMapping("/{code}/load")
    @PermissionRegister(code = "platform:plugin:manage", name = "加载插件", module = "平台插件", desc = "加载插件 JAR 到运行时")
    public Result<PluginModuleRes> load(@PathVariable String code) {
        return Result.ok(PluginWebAssembler.toRes(pluginAppService.load(code)));
    }

    @PostMapping("/{code}/enable")
    @PermissionRegister(code = "platform:plugin:manage", name = "启用插件", module = "平台插件", desc = "启用插件并注册运行时扩展")
    public Result<PluginModuleRes> enable(@PathVariable String code) {
        return Result.ok(PluginWebAssembler.toRes(pluginAppService.enable(code)));
    }

    @PostMapping("/{code}/disable")
    @PermissionRegister(code = "platform:plugin:manage", name = "禁用插件", module = "平台插件", desc = "禁用插件并移除运行时扩展")
    public Result<PluginModuleRes> disable(@PathVariable String code) {
        return Result.ok(PluginWebAssembler.toRes(pluginAppService.disable(code)));
    }

    @PostMapping("/{code}/unload")
    @PermissionRegister(code = "platform:plugin:manage", name = "卸载插件", module = "平台插件", desc = "卸载插件并释放 ClassLoader")
    public Result<PluginModuleRes> unload(@PathVariable String code) {
        return Result.ok(PluginWebAssembler.toRes(pluginAppService.unload(code)));
    }

    @GetMapping("/frontend-manifest")
    public Result<PluginFrontendManifestRes> frontendManifest() {
        return Result.ok(PluginWebAssembler.toRes(pluginAppService.frontendManifest()));
    }
}
