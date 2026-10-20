package online.yudream.base.interfaces.platform.cms.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.service.StudioAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.cms.assembler.StudioWebAssembler;
import online.yudream.base.interfaces.platform.cms.request.*;
import online.yudream.base.interfaces.platform.cms.res.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/cms/studio/{themeCode}")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class StudioController {
    private final StudioAppService studioAppService;

    @GetMapping
    @PermissionRegister(code = "platform:cms:view", name = "查看站点工作台", module = "内容定制", desc = "查看主题隔离的结构化站点草稿")
    public Result<StudioStateRes> state(@PathVariable String themeCode) {
        return Result.ok(StudioWebAssembler.toRes(studioAppService.state(themeCode)));
    }
    @PutMapping("/draft")
    @PermissionRegister(code = "platform:cms:edit", name = "编辑站点草稿", module = "内容定制", desc = "按预期版本保存结构化站点草稿")
    public Result<StudioStateRes> draft(@PathVariable String themeCode, @Valid @RequestBody StudioDraftSaveRequest request) {
        return Result.ok(StudioWebAssembler.toRes(studioAppService.saveDraft(themeCode, StudioWebAssembler.toCmd(request))));
    }
    @PostMapping("/publish")
    @PermissionRegister(code = "platform:cms:publish", name = "发布站点快照", module = "内容定制", desc = "校验草稿并原子切换不可变发布快照")
    public Result<StudioStateRes> publish(@PathVariable String themeCode, @Valid @RequestBody StudioRevisionRequest request) {
        return Result.ok(StudioWebAssembler.toRes(studioAppService.publish(themeCode, StudioWebAssembler.toCmd(request))));
    }
    @PostMapping("/unpublish")
    @PermissionRegister(code = "platform:cms:publish", name = "停用新版站点", module = "内容定制", desc = "仅清除发布指针，保留站点草稿与发布快照以便再次启用")
    public Result<StudioStateRes> unpublish(@PathVariable String themeCode, @Valid @RequestBody StudioRevisionRequest request) {
        return Result.ok(StudioWebAssembler.toRes(studioAppService.unpublish(themeCode, StudioWebAssembler.toCmd(request))));
    }
    @GetMapping("/releases")
    @PermissionRegister(code = "platform:cms:view", name = "查看站点发布版本", module = "内容定制", desc = "分页查看保留范围内的发布快照")
    public Result<StudioReleasePageRes> releases(@PathVariable String themeCode,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size) {
        return Result.ok(StudioWebAssembler.toRes(studioAppService.releases(themeCode, page, size)));
    }
    @PostMapping("/releases/{releaseId}/restore")
    @PermissionRegister(code = "platform:cms:edit", name = "恢复站点草稿", module = "内容定制", desc = "发布快照仅恢复至草稿，不改变线上发布指针")
    public Result<StudioStateRes> restore(@PathVariable String themeCode, @PathVariable String releaseId,
            @Valid @RequestBody StudioRevisionRequest request) {
        return Result.ok(StudioWebAssembler.toRes(studioAppService.restore(themeCode, releaseId, StudioWebAssembler.toCmd(request))));
    }
}
