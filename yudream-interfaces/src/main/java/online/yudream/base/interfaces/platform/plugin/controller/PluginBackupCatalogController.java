package online.yudream.base.interfaces.platform.plugin.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginBackupCatalogAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.res.PluginBackupTargetCatalogRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 插件前端备份目录：启用中的异地备份目标列表，
 * 供插件界面（如 MC 面板计划任务/手动备份）渲染统一目标选择器。
 */
@RestController
@RequestMapping("/api/platform/plugins/backup")
@RequiredArgsConstructor
public class PluginBackupCatalogController {

    private final PluginBackupCatalogAppService catalogAppService;

    @GetMapping("/targets")
    @PermissionRegister(code = "system:backup:view", name = "查看备份中心", module = "数据备份", desc = "查看备份范围、任务与配置")
    public Result<List<PluginBackupTargetCatalogRes>> targets() {
        StpUtil.checkPermission("system:backup:view");
        return Result.ok(PluginWebAssembler.toBackupTargetCatalogResList(catalogAppService.targets()));
    }
}
