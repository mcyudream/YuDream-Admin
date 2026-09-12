package online.yudream.base.interfaces.platform.plugin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.application.platform.plugin.service.PluginMarketPublicationAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginMarketPublicationWebAssembler;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketPublicationReviewRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketReviewRequiredRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketPublicationRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 自托管市场源发布与审核。界面上传与 API Key 流水线共用同一发布端点：
 * API Key 过滤器 + PermissionRegisterAspect 天然兼容，通道按是否存在 API Key 鉴权自动判定。
 */
@RestController
@RequestMapping("/api/platform/plugin-market-source/publications")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.plugin-market-source", name = "enabled", havingValue = "true")
public class PluginMarketPublicationController {

    private final PluginMarketPublicationAppService pluginMarketPublicationAppService;

    @PostMapping
    @PermissionRegister(code = "platform:plugin-market-source:upload", name = "发布插件到市场源", module = "平台插件市场源",
            desc = "上传插件 JAR 到本机市场源；reviewRequired 开启时进入待审核")
    public Result<PluginMarketPublicationRes> publish(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "releaseNotes", required = false) String releaseNotes,
                                                      @RequestParam(value = "metadata", required = false) String metadata) throws IOException {
        boolean pipeline = SecurityPrincipalSupport.hasApiKeyAuthentication();
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.publish(
                file.getInputStream(), file.getSize(), releaseNotes, metadata,
                SecurityPrincipalSupport.current().userId(), pipeline)));
    }

    @GetMapping
    @PermissionRegister(code = "platform:plugin-market-source:view", name = "查看插件市场源", module = "平台插件市场源",
            desc = "查看发布物列表，可按状态过滤")
    public Result<List<PluginMarketPublicationRes>> list(@RequestParam(value = "status", required = false) String status) {
        return Result.ok(PluginMarketPublicationWebAssembler.toResList(pluginMarketPublicationAppService.list(status)));
    }

    @PostMapping("/{id}/accept")
    @PermissionRegister(code = "platform:plugin-market-source:accept", name = "审核市场发布物", module = "平台插件市场源",
            desc = "通过待审核的发布物，通过后对外可见")
    public Result<PluginMarketPublicationRes> accept(@PathVariable String id,
                                                     @Valid @RequestBody(required = false) PluginMarketPublicationReviewRequest request) {
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.accept(
                PluginMarketPublicationWebAssembler.toReviewCmd(id, request),
                SecurityPrincipalSupport.current().userId())));
    }

    @PostMapping("/{id}/reject")
    @PermissionRegister(code = "platform:plugin-market-source:accept", name = "审核市场发布物", module = "平台插件市场源",
            desc = "拒绝待审核的发布物")
    public Result<PluginMarketPublicationRes> reject(@PathVariable String id,
                                                     @Valid @RequestBody(required = false) PluginMarketPublicationReviewRequest request) {
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.reject(
                PluginMarketPublicationWebAssembler.toReviewCmd(id, request),
                SecurityPrincipalSupport.current().userId())));
    }

    @PostMapping("/{id}/unpublish")
    @PermissionRegister(code = "platform:plugin-market-source:delete", name = "删除插件市场源", module = "平台插件市场源",
            desc = "下架已发布的版本，文件保留备查但不再对外下发")
    public Result<PluginMarketPublicationRes> unpublish(@PathVariable String id,
                                                        @Valid @RequestBody(required = false) PluginMarketPublicationReviewRequest request) {
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.unpublish(
                PluginMarketPublicationWebAssembler.toReviewCmd(id, request),
                SecurityPrincipalSupport.current().userId())));
    }

    @GetMapping("/review-required")
    @PermissionRegister(code = "platform:plugin-market-source:view", name = "查看插件市场源", module = "平台插件市场源",
            desc = "查看发布是否需要审核")
    public Result<Boolean> reviewRequired() {
        return Result.ok(pluginMarketPublicationAppService.reviewRequired());
    }

    @PutMapping("/review-required")
    @PermissionRegister(code = "platform:plugin-market-source:edit", name = "编辑插件市场源", module = "平台插件市场源",
            desc = "设置发布是否需要审核；关闭后界面上传与流水线发布直接对外可见")
    public Result<Boolean> updateReviewRequired(@Valid @RequestBody PluginMarketReviewRequiredRequest request) {
        return Result.ok(pluginMarketPublicationAppService.updateReviewRequired(
                Boolean.TRUE.equals(request.getReviewRequired())));
    }
}
