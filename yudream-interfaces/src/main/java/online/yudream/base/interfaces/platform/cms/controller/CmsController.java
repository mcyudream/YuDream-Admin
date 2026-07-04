package online.yudream.base.interfaces.platform.cms.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.query.CmsPageQuery;
import online.yudream.base.application.platform.cms.service.CmsAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.cms.assembler.CmsWebAssembler;
import online.yudream.base.interfaces.platform.cms.request.CmsPageSaveRequest;
import online.yudream.base.interfaces.platform.cms.request.HomePageLayoutSaveRequest;
import online.yudream.base.interfaces.platform.cms.res.CmsPageRes;
import online.yudream.base.interfaces.platform.cms.res.HomePageLayoutRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/cms")
@RequiredArgsConstructor
public class CmsController {

    private final CmsAppService cmsAppService;

    @GetMapping("/pages")
    @PermissionRegister(code = "platform:cms:view", name = "查看内容页面", module = "平台能力", desc = "查看内容定制页面列表")
    public Result<PageResult<CmsPageRes>> pages(CmsPageQuery query) {
        return Result.ok(CmsWebAssembler.toPage(cmsAppService.page(query)));
    }

    @PostMapping("/pages")
    @PermissionRegister(code = "platform:cms:edit", name = "新增内容页面", module = "平台能力", desc = "新增 Markdown 内容页面")
    public Result<CmsPageRes> createPage(@Valid @RequestBody CmsPageSaveRequest request) {
        return Result.ok(CmsWebAssembler.toRes(cmsAppService.savePage(CmsWebAssembler.toCmd(request))));
    }

    @PutMapping("/pages/{id}")
    @PermissionRegister(code = "platform:cms:edit", name = "编辑内容页面", module = "平台能力", desc = "编辑 Markdown 内容页面")
    public Result<CmsPageRes> updatePage(@PathVariable Long id, @Valid @RequestBody CmsPageSaveRequest request) {
        return Result.ok(CmsWebAssembler.toRes(cmsAppService.savePage(CmsWebAssembler.toCmd(id, request))));
    }

    @PostMapping("/pages/{id}/publish")
    @PermissionRegister(code = "platform:cms:publish", name = "发布内容页面", module = "平台能力", desc = "发布内容页面")
    public Result<Void> publish(@PathVariable Long id) {
        cmsAppService.publish(id);
        return Result.ok();
    }

    @PostMapping("/pages/{id}/unpublish")
    @PermissionRegister(code = "platform:cms:publish", name = "取消发布内容页面", module = "平台能力", desc = "取消发布内容页面")
    public Result<Void> unpublish(@PathVariable Long id) {
        cmsAppService.unpublish(id);
        return Result.ok();
    }

    @DeleteMapping("/pages/{id}")
    @PermissionRegister(code = "platform:cms:delete", name = "删除内容页面", module = "平台能力", desc = "删除内容定制页面")
    public Result<Void> deletePage(@PathVariable Long id) {
        cmsAppService.deletePage(id);
        return Result.ok();
    }

    @GetMapping("/home")
    @PermissionRegister(code = "platform:cms:view", name = "查看首页配置", module = "平台能力", desc = "查看自定义首页配置")
    public Result<HomePageLayoutRes> home() {
        return Result.ok(CmsWebAssembler.toRes(cmsAppService.homeLayout()));
    }

    @PutMapping("/home")
    @PermissionRegister(code = "platform:cms:edit", name = "编辑首页配置", module = "平台能力", desc = "编辑自定义首页配置")
    public Result<HomePageLayoutRes> saveHome(@RequestBody HomePageLayoutSaveRequest request) {
        return Result.ok(CmsWebAssembler.toRes(cmsAppService.saveHomeLayout(CmsWebAssembler.toCmd(request))));
    }
}
