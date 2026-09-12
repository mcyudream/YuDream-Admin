package online.yudream.base.interfaces.platform.plugin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginMarketSourceAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginMarketSourceWebAssembler;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketSourceCreateRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketSourceTestRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketSourceUpdateRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketSourceRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketSourceTestResultRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 插件市场源管理。项目闸门关闭时整个控制器不注册，市场目录为空（无 Nexus 回落）；
 * 应用闸门由 PluginMarketSourceAppService.ensureEnabled 在各用例内校验。
 */
@RestController
@RequestMapping("/api/platform/plugin-market-sources")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.plugin-market-source", name = "enabled", havingValue = "true")
public class PluginMarketSourceController {

    private final PluginMarketSourceAppService pluginMarketSourceAppService;

    @GetMapping
    @PermissionRegister(code = "platform:plugin-market-source:view", name = "查看插件市场源", module = "平台插件市场源", desc = "查看已配置的插件市场源列表")
    public Result<List<PluginMarketSourceRes>> list() {
        return Result.ok(PluginMarketSourceWebAssembler.toResList(pluginMarketSourceAppService.list()));
    }

    @PostMapping
    @PermissionRegister(code = "platform:plugin-market-source:create", name = "添加插件市场源", module = "平台插件市场源", desc = "添加一个新的插件市场源")
    public Result<PluginMarketSourceRes> create(@Valid @RequestBody PluginMarketSourceCreateRequest request) {
        return Result.ok(PluginMarketSourceWebAssembler.toRes(pluginMarketSourceAppService.create(
                PluginMarketSourceWebAssembler.toCreateCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:plugin-market-source:edit", name = "编辑插件市场源", module = "平台插件市场源", desc = "编辑插件市场源名称、地址、令牌或排序")
    public Result<PluginMarketSourceRes> update(@PathVariable String id,
                                                @Valid @RequestBody PluginMarketSourceUpdateRequest request) {
        return Result.ok(PluginMarketSourceWebAssembler.toRes(pluginMarketSourceAppService.update(
                PluginMarketSourceWebAssembler.toUpdateCmd(PluginMarketSourceWebAssembler.parseId(id), request))));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "platform:plugin-market-source:delete", name = "删除插件市场源", module = "平台插件市场源", desc = "删除插件市场源及其目录快照")
    public Result<Void> delete(@PathVariable String id) {
        pluginMarketSourceAppService.delete(PluginMarketSourceWebAssembler.parseId(id));
        return Result.ok(null);
    }

    @PostMapping("/{id}/enable")
    @PermissionRegister(code = "platform:plugin-market-source:edit", name = "编辑插件市场源", module = "平台插件市场源", desc = "启用插件市场源")
    public Result<PluginMarketSourceRes> enable(@PathVariable String id) {
        return Result.ok(PluginMarketSourceWebAssembler.toRes(
                pluginMarketSourceAppService.enable(PluginMarketSourceWebAssembler.parseId(id))));
    }

    @PostMapping("/{id}/disable")
    @PermissionRegister(code = "platform:plugin-market-source:edit", name = "编辑插件市场源", module = "平台插件市场源", desc = "禁用插件市场源")
    public Result<PluginMarketSourceRes> disable(@PathVariable String id) {
        return Result.ok(PluginMarketSourceWebAssembler.toRes(
                pluginMarketSourceAppService.disable(PluginMarketSourceWebAssembler.parseId(id))));
    }

    @PostMapping("/{id}/sync")
    @PermissionRegister(code = "platform:plugin-market-source:run", name = "同步插件市场源", module = "平台插件市场源", desc = "拉取插件市场源的最新目录快照")
    public Result<PluginMarketSourceRes> sync(@PathVariable String id) {
        return Result.ok(PluginMarketSourceWebAssembler.toRes(
                pluginMarketSourceAppService.sync(PluginMarketSourceWebAssembler.parseId(id))));
    }

    @PostMapping("/sync-all")
    @PermissionRegister(code = "platform:plugin-market-source:run", name = "同步插件市场源", module = "平台插件市场源", desc = "同步全部已启用的插件市场源")
    public Result<List<PluginMarketSourceRes>> syncAll() {
        return Result.ok(PluginMarketSourceWebAssembler.toResList(pluginMarketSourceAppService.syncAll()));
    }

    @PostMapping("/test")
    @PermissionRegister(code = "platform:plugin-market-source:run", name = "同步插件市场源", module = "平台插件市场源", desc = "测试尚未保存的市场源地址是否可用")
    public Result<PluginMarketSourceTestResultRes> test(@Valid @RequestBody PluginMarketSourceTestRequest request) {
        return Result.ok(PluginMarketSourceWebAssembler.toTestRes(
                pluginMarketSourceAppService.test(PluginMarketSourceWebAssembler.toTestCmd(request))));
    }
}
