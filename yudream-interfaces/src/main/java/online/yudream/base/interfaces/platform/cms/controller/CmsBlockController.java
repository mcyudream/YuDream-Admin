package online.yudream.base.interfaces.platform.cms.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.query.CmsBlockQuery;
import online.yudream.base.application.platform.cms.service.CmsBlockAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.cms.assembler.CmsWebAssembler;
import online.yudream.base.interfaces.platform.cms.request.CmsBlockSaveRequest;
import online.yudream.base.interfaces.platform.cms.res.CmsBlockRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/cms/blocks")
@RequiredArgsConstructor
public class CmsBlockController {

    private final CmsBlockAppService cmsBlockAppService;

    @GetMapping
    @PermissionRegister(code = "platform:cms:view", name = "查看 CMS 区块", module = "平台能力", desc = "查看 CMS 区块列表")
    public Result<PageResult<CmsBlockRes>> blocks(CmsBlockQuery query) {
        return Result.ok(CmsWebAssembler.toBlockPage(cmsBlockAppService.page(query)));
    }

    @GetMapping("/{id}")
    @PermissionRegister(code = "platform:cms:view", name = "查看 CMS 区块详情", module = "平台能力", desc = "查看单个 CMS 区块详情")
    public Result<CmsBlockRes> getById(@PathVariable Long id) {
        return Result.ok(CmsWebAssembler.toBlockRes(cmsBlockAppService.getById(id)));
    }

    @PostMapping
    @PermissionRegister(code = "platform:cms:edit", name = "新增 CMS 区块", module = "平台能力", desc = "新增 CMS 区块")
    public Result<CmsBlockRes> create(@Valid @RequestBody CmsBlockSaveRequest request) {
        return Result.ok(CmsWebAssembler.toBlockRes(cmsBlockAppService.create(CmsWebAssembler.toBlockCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:cms:edit", name = "编辑 CMS 区块", module = "平台能力", desc = "编辑 CMS 区块")
    public Result<CmsBlockRes> update(@PathVariable Long id, @Valid @RequestBody CmsBlockSaveRequest request) {
        return Result.ok(CmsWebAssembler.toBlockRes(cmsBlockAppService.update(id, CmsWebAssembler.toBlockCmd(request))));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "platform:cms:delete", name = "删除 CMS 区块", module = "平台能力", desc = "删除 CMS 区块")
    public Result<Void> delete(@PathVariable Long id) {
        cmsBlockAppService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/enable")
    @PermissionRegister(code = "platform:cms:edit", name = "启用 CMS 区块", module = "平台能力", desc = "启用 CMS 区块")
    public Result<Void> enable(@PathVariable Long id) {
        cmsBlockAppService.enable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/disable")
    @PermissionRegister(code = "platform:cms:edit", name = "禁用 CMS 区块", module = "平台能力", desc = "禁用 CMS 区块")
    public Result<Void> disable(@PathVariable Long id) {
        cmsBlockAppService.disable(id);
        return Result.ok();
    }

    @GetMapping("/categories")
    @PermissionRegister(code = "platform:cms:view", name = "查看 CMS 区块分类", module = "平台能力", desc = "查看 CMS 区块分类列表")
    public Result<List<String>> categories() {
        return Result.ok(cmsBlockAppService.listCategories());
    }
}
