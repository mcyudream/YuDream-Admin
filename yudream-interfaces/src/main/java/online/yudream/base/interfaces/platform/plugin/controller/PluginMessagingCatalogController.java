package online.yudream.base.interfaces.platform.plugin.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
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
    public Result<List<PluginMessagingConnectionRes>> connections() {
        StpUtil.checkLogin();
        return Result.ok(PluginWebAssembler.toMessagingConnectionResList(catalogAppService.connections()));
    }

    @GetMapping("/groups")
    public Result<List<PluginMessagingGroupRes>> groups(@RequestParam(required = false) String connectionId) {
        StpUtil.checkLogin();
        return Result.ok(PluginWebAssembler.toMessagingGroupResList(catalogAppService.groups(connectionId)));
    }
}
