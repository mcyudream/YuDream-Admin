package online.yudream.base.interfaces.platform.plugin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.query.PluginMarketPublicationPageQuery;
import online.yudream.base.application.platform.plugin.service.PluginMarketPublicationAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginMarketPublicationWebAssembler;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketPublicationEditRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketPublicationReviewRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketReviewRequiredRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketPublicationRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private static final String PERM_VIEW = "platform:plugin-market-source:view";
    private static final String PERM_EDIT = "platform:plugin-market-source:edit";
    private static final String PERM_DELETE = "platform:plugin-market-source:delete";
    private static final String PERM_UPLOAD = "platform:plugin-market-source:upload";
    private static final String PERM_ACCEPT = "platform:plugin-market-source:accept";
    private static final String PERM_PUBLISH = "platform:plugin-market-source:publish";

    private final PluginMarketPublicationAppService pluginMarketPublicationAppService;

    @PostMapping
    @PermissionRegister(code = PERM_UPLOAD, name = "发布插件到市场源", module = "平台插件市场源",
            desc = "上传插件 JAR 到本机市场源；reviewRequired 开启时进入待审核，持有跳过审核权限则直接发布")
    public Result<PluginMarketPublicationRes> publish(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "releaseNotes", required = false) String releaseNotes,
                                                      @RequestParam(value = "metadata", required = false) String metadata,
                                                      @RequestParam(value = "category", required = false) String category,
                                                      @RequestParam(value = "tags", required = false) String tags) throws IOException {
        boolean pipeline = SecurityPrincipalSupport.hasApiKeyAuthentication();
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.publish(
                file.getInputStream(), file.getSize(), releaseNotes, metadata, category, splitTags(tags),
                SecurityPrincipalSupport.current().userId(), pipeline,
                SecurityPrincipalSupport.hasPermission(PERM_PUBLISH))));
    }

    @PutMapping("/{id}")
    public Result<PluginMarketPublicationRes> edit(@PathVariable String id,
                                                   @Valid @RequestBody PluginMarketPublicationEditRequest request) {
        ensureAny("无权限编辑发布物", PERM_EDIT, PERM_UPLOAD, PERM_ACCEPT);
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(
                pluginMarketPublicationAppService.edit(
                        PluginMarketPublicationWebAssembler.toEditCmd(id, request),
                        SecurityPrincipalSupport.current().userId(),
                        canManageOthers(PERM_EDIT))));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        ensureAny("无权限删除发布物", PERM_DELETE, PERM_UPLOAD, PERM_ACCEPT);
        pluginMarketPublicationAppService.delete(
                PluginMarketPublicationWebAssembler.parseId(id),
                SecurityPrincipalSupport.current().userId(),
                canManageOthers(PERM_DELETE));
        return Result.ok(null);
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split("[,，]"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @GetMapping
    public Result<PageResult<PluginMarketPublicationRes>> list(PluginMarketPublicationPageQuery query) {
        ensureAny("无权限查看发布物", PERM_VIEW, PERM_UPLOAD, PERM_ACCEPT);
        boolean canViewAll = SecurityPrincipalSupport.hasPermission(PERM_VIEW)
                || SecurityPrincipalSupport.hasPermission(PERM_ACCEPT);
        return Result.ok(PluginMarketPublicationWebAssembler.toResPage(
                pluginMarketPublicationAppService.page(query, SecurityPrincipalSupport.current().userId(), canViewAll)));
    }

    @GetMapping("/skip-review")
    @PermissionRegister(code = PERM_PUBLISH, name = "跳过审核直接发布", module = "平台插件市场源",
            desc = "持有该权限时上传直接对外可见，无视全局审核开关")
    public Result<Boolean> skipReview() {
        return Result.ok(Boolean.TRUE);
    }

    @PostMapping("/{id}/accept")
    @PermissionRegister(code = PERM_ACCEPT, name = "审核市场发布物", module = "平台插件市场源",
            desc = "通过待审核的发布物，通过后对外可见")
    public Result<PluginMarketPublicationRes> accept(@PathVariable String id,
                                                     @Valid @RequestBody(required = false) PluginMarketPublicationReviewRequest request) {
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.accept(
                PluginMarketPublicationWebAssembler.toReviewCmd(id, request),
                SecurityPrincipalSupport.current().userId())));
    }

    @PostMapping("/{id}/reject")
    @PermissionRegister(code = PERM_ACCEPT, name = "审核市场发布物", module = "平台插件市场源",
            desc = "拒绝待审核的发布物")
    public Result<PluginMarketPublicationRes> reject(@PathVariable String id,
                                                     @Valid @RequestBody(required = false) PluginMarketPublicationReviewRequest request) {
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.reject(
                PluginMarketPublicationWebAssembler.toReviewCmd(id, request),
                SecurityPrincipalSupport.current().userId())));
    }

    @PostMapping("/{id}/unpublish")
    public Result<PluginMarketPublicationRes> unpublish(@PathVariable String id,
                                                        @Valid @RequestBody(required = false) PluginMarketPublicationReviewRequest request) {
        ensureAny("无权限下架发布物", PERM_DELETE, PERM_UPLOAD, PERM_ACCEPT);
        return Result.ok(PluginMarketPublicationWebAssembler.toRes(pluginMarketPublicationAppService.unpublish(
                PluginMarketPublicationWebAssembler.toReviewCmd(id, request),
                SecurityPrincipalSupport.current().userId(),
                canManageOthers(PERM_DELETE))));
    }

    @GetMapping("/review-required")
    public Result<Boolean> reviewRequired() {
        ensureAny("无权限查看审核配置", PERM_VIEW, PERM_UPLOAD, PERM_ACCEPT);
        return Result.ok(pluginMarketPublicationAppService.reviewRequired());
    }

    @PutMapping("/review-required")
    @PermissionRegister(code = PERM_EDIT, name = "编辑插件市场源", module = "平台插件市场源",
            desc = "设置发布是否需要审核；关闭后界面上传与流水线发布直接对外可见")
    public Result<Boolean> updateReviewRequired(@Valid @RequestBody PluginMarketReviewRequiredRequest request) {
        return Result.ok(pluginMarketPublicationAppService.updateReviewRequired(
                Boolean.TRUE.equals(request.getReviewRequired())));
    }

    private boolean canManageOthers(String sourceManagePermission) {
        return SecurityPrincipalSupport.hasPermission(PERM_ACCEPT)
                || SecurityPrincipalSupport.hasPermission(sourceManagePermission);
    }

    private void ensureAny(String message, String... permissions) {
        SecurityPrincipalSupport.current();
        for (String permission : permissions) {
            if (SecurityPrincipalSupport.hasPermission(permission)) {
                return;
            }
        }
        throw new BizException(message);
    }
}
