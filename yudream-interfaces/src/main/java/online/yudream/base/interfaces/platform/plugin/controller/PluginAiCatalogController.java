package online.yudream.base.interfaces.platform.plugin.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginAiCatalogAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.platform.plugin.res.PluginAiAgentCatalogRes;
import online.yudream.base.interfaces.platform.plugin.res.PluginAiProviderCatalogRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/plugins/ai")
@RequiredArgsConstructor
public class PluginAiCatalogController {

    private final PluginAiCatalogAppService catalogAppService;

    @GetMapping("/agents")
    public Result<List<PluginAiAgentCatalogRes>> agents() {
        StpUtil.checkLogin();
        return Result.ok(PluginWebAssembler.toAiAgentCatalogResList(catalogAppService.agents()));
    }

    @GetMapping("/providers")
    public Result<List<PluginAiProviderCatalogRes>> providers() {
        StpUtil.checkLogin();
        return Result.ok(PluginWebAssembler.toAiProviderCatalogResList(catalogAppService.providers()));
    }
}
