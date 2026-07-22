package online.yudream.base.interfaces.platform.plugin.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.dto.PluginFrontendAssetDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.res.PluginFrontendManifestRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginModuleRes;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    @PermissionRegister(code = "platform:plugin:manage", name = "管理插件", module = "平台插件", desc = "扫描、加载、启用、禁用、卸载和删除插件")
    public Result<List<PluginModuleRes>> refresh() {
        return Result.ok(PluginWebAssembler.toResList(pluginAppService.refresh()));
    }

    @PostMapping("/upload")
    @PermissionRegister(code = "platform:plugin:manage", name = "上传插件", module = "平台插件", desc = "上传插件 JAR 并替换同编码插件")
    public Result<List<PluginModuleRes>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(PluginWebAssembler.toResList(pluginAppService.upload(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getSize()
        )));
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

    @DeleteMapping("/{code}")
    @PermissionRegister(code = "platform:plugin:manage", name = "删除插件", module = "平台插件", desc = "删除插件持久化记录")
    public Result<Void> delete(@PathVariable String code) {
        pluginAppService.delete(code);
        return Result.ok();
    }

    @GetMapping("/frontend-manifest")
    public Result<PluginFrontendManifestRes> frontendManifest() {
        // 未登录访客只下发 publicAccess 路由（匿名公开页面注册用）
        return Result.ok(PluginWebAssembler.toRes(pluginAppService.frontendManifest(!StpUtil.isLogin())));
    }

    @GetMapping("/{code}/assets/**")
    public ResponseEntity<byte[]> frontendAsset(@PathVariable String code, HttpServletRequest request) {
        PluginFrontendAssetDTO asset = pluginAppService.frontendAsset(code, PluginWebAssembler.frontendAssetPath(code, request));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .body(asset.getBody());
    }
}
