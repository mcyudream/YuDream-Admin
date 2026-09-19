package online.yudream.base.interfaces.platform.plugin.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.application.platform.plugin.service.PluginMessagingCatalogAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.res.PluginMessagingConnectionRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginMessagingGroupRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/plugins/messaging")
@RequiredArgsConstructor
public class PluginMessagingCatalogController {

    private final PluginMessagingCatalogAppService catalogAppService;

    @GetMapping("/connections")
    @PermissionRegister(code = "platform:milky:view", name = "查看 QQ 消息连接", module = "QQ 消息平台", desc = "查看消息连接目录")
    public Result<List<PluginMessagingConnectionRes>> connections() {
        StpUtil.checkPermission("platform:milky:view");
        return Result.ok(PluginWebAssembler.toMessagingConnectionResList(catalogAppService.connections()));
    }

    @GetMapping("/groups")
    @PermissionRegister(code = "platform:milky:view", name = "查看 QQ 消息连接", module = "QQ 消息平台", desc = "查看消息连接群组目录")
    public Result<List<PluginMessagingGroupRes>> groups(@RequestParam(required = false) String connectionId) {
        StpUtil.checkPermission("platform:milky:view");
        return Result.ok(PluginWebAssembler.toMessagingGroupResList(catalogAppService.groups(connectionId)));
    }
}
