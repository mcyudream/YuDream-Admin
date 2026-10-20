package online.yudream.base.interfaces.platform.cms.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.cms.service.StudioBlockTemplateAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.cms.assembler.StudioBlockTemplateWebAssembler;
import online.yudream.base.interfaces.platform.cms.request.StudioBlockTemplateSaveRequest;
import online.yudream.base.interfaces.platform.cms.res.StudioBlockTemplateRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/platform/cms/block-templates")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.cms", name = "enabled", havingValue = "true")
public class StudioBlockTemplateController {
    private final StudioBlockTemplateAppService templateAppService;

    @GetMapping
    @PermissionRegister(code = "platform:cms:block-template:view", name = "查看区块模板", module = "内容定制", desc = "在网站工作台插入器中查看与插入站点共享的区块模板")
    public Result<List<StudioBlockTemplateRes>> list() {
        return Result.ok(templateAppService.list().stream().map(StudioBlockTemplateWebAssembler::toRes).toList());
    }

    @PostMapping
    @PermissionRegister(code = "platform:cms:block-template:create", name = "创建区块模板", module = "内容定制", desc = "把选中的站点元素保存为站点共享的区块模板")
    public Result<StudioBlockTemplateRes> create(@Valid @RequestBody StudioBlockTemplateSaveRequest request) {
        return Result.ok(StudioBlockTemplateWebAssembler.toRes(templateAppService.create(StudioBlockTemplateWebAssembler.toCmd(request), StpUtil.getLoginIdAsLong())));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:cms:block-template:edit", name = "编辑区块模板", module = "内容定制", desc = "按版本修改区块模板名称、分类、说明与内容")
    public Result<StudioBlockTemplateRes> update(@PathVariable String id, @Valid @RequestBody StudioBlockTemplateSaveRequest request) {
        return Result.ok(StudioBlockTemplateWebAssembler.toRes(templateAppService.update(id, StudioBlockTemplateWebAssembler.toCmd(request))));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "platform:cms:block-template:delete", name = "删除区块模板", module = "内容定制", desc = "删除区块模板，已插入页面的内容不受影响")
    public Result<Void> delete(@PathVariable String id) {
        templateAppService.delete(id);
        return Result.ok();
    }
}
