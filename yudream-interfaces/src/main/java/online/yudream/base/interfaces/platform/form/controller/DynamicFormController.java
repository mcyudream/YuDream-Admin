package online.yudream.base.interfaces.platform.form.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.form.query.DynamicFormPageQuery;
import online.yudream.base.application.platform.form.query.FormSubmissionPageQuery;
import online.yudream.base.application.platform.form.service.DynamicFormAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.form.assembler.DynamicFormWebAssembler;
import online.yudream.base.interfaces.platform.form.request.DynamicFormSaveRequest;
import online.yudream.base.interfaces.platform.form.res.DynamicFormRes;
import online.yudream.base.interfaces.platform.form.res.FormStatisticsRes;
import online.yudream.base.interfaces.platform.form.res.FormSubmissionRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/forms")
@RequiredArgsConstructor
public class DynamicFormController {

    private final DynamicFormAppService dynamicFormAppService;

    @GetMapping
    @PermissionRegister(code = "platform:form:view", name = "查看动态表单", module = "平台能力", desc = "查看动态表单列表")
    public Result<PageResult<DynamicFormRes>> forms(DynamicFormPageQuery query) {
        return Result.ok(DynamicFormWebAssembler.toPage(dynamicFormAppService.page(query)));
    }

    @GetMapping("/{id}")
    @PermissionRegister(code = "platform:form:view", name = "查看动态表单详情", module = "平台能力", desc = "查看动态表单设计详情")
    public Result<DynamicFormRes> detail(@PathVariable Long id) {
        return Result.ok(DynamicFormWebAssembler.toRes(dynamicFormAppService.detail(id)));
    }

    @PostMapping
    @PermissionRegister(code = "platform:form:edit", name = "新增动态表单", module = "平台能力", desc = "新增动态表单")
    public Result<DynamicFormRes> create(@Valid @RequestBody DynamicFormSaveRequest request) {
        return Result.ok(DynamicFormWebAssembler.toRes(dynamicFormAppService.save(DynamicFormWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:form:edit", name = "编辑动态表单", module = "平台能力", desc = "编辑动态表单")
    public Result<DynamicFormRes> update(@PathVariable Long id, @Valid @RequestBody DynamicFormSaveRequest request) {
        return Result.ok(DynamicFormWebAssembler.toRes(dynamicFormAppService.save(DynamicFormWebAssembler.toCmd(id, request))));
    }

    @PostMapping("/{id}/publish")
    @PermissionRegister(code = "platform:form:publish", name = "发布动态表单", module = "平台能力", desc = "发布动态表单")
    public Result<Void> publish(@PathVariable Long id) {
        dynamicFormAppService.publish(id);
        return Result.ok();
    }

    @PostMapping("/{id}/unpublish")
    @PermissionRegister(code = "platform:form:publish", name = "取消发布动态表单", module = "平台能力", desc = "取消发布动态表单")
    public Result<Void> unpublish(@PathVariable Long id) {
        dynamicFormAppService.unpublish(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "platform:form:delete", name = "删除动态表单", module = "平台能力", desc = "删除动态表单")
    public Result<Void> delete(@PathVariable Long id) {
        dynamicFormAppService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}/submissions")
    @PermissionRegister(code = "platform:form:submission:view", name = "查看表单提交", module = "平台能力", desc = "查看动态表单提交记录")
    public Result<PageResult<FormSubmissionRes>> submissions(@PathVariable Long id, FormSubmissionPageQuery query) {
        return Result.ok(DynamicFormWebAssembler.toSubmissionPage(
                dynamicFormAppService.submissions(DynamicFormWebAssembler.toSubmissionQuery(id, query))
        ));
    }

    @GetMapping("/{id}/statistics")
    @PermissionRegister(code = "platform:form:statistics:view", name = "查看表单统计", module = "平台能力", desc = "查看动态表单结果统计")
    public Result<FormStatisticsRes> statistics(@PathVariable Long id) {
        return Result.ok(DynamicFormWebAssembler.toRes(dynamicFormAppService.statistics(id)));
    }
}
