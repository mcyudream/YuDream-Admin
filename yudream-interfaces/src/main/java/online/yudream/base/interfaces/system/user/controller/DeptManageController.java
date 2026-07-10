package online.yudream.base.interfaces.system.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.query.DeptTreeQuery;
import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.user.assembler.UserManageWebAssembler;
import online.yudream.base.interfaces.system.user.request.DeptCreateRequest;
import online.yudream.base.interfaces.system.user.request.DeptUpdateRequest;
import online.yudream.base.interfaces.system.user.res.DeptManageRes;
import online.yudream.base.interfaces.system.user.res.OptionRes;
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
@RequestMapping("/api/system/depts")
@RequiredArgsConstructor
public class DeptManageController {

    private final DeptManageAppService deptManageAppService;

    @GetMapping
    public Result<List<DeptManageRes>> tree(DeptTreeQuery query) {
        StpUtil.checkLogin();
        return Result.ok(UserManageWebAssembler.toDeptResList(deptManageAppService.tree(query)));
    }

    @GetMapping("/options")
    public Result<List<OptionRes>> options() {
        StpUtil.checkLogin();
        return Result.ok(UserManageWebAssembler.toOptionResList(deptManageAppService.options()));
    }

    @PostMapping
    @PermissionRegister(code = "system:dept:create", name = "新增部门", module = "系统管理", desc = "新增部门")
    public Result<DeptManageRes> create(@Valid @RequestBody DeptCreateRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(deptManageAppService.create(UserManageWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "system:dept:edit", name = "编辑部门", module = "系统管理", desc = "编辑部门")
    public Result<DeptManageRes> update(@PathVariable Long id, @Valid @RequestBody DeptUpdateRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(deptManageAppService.update(UserManageWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "system:dept:delete", name = "删除部门", module = "系统管理", desc = "停用部门")
    public Result<Void> disable(@PathVariable Long id) {
        deptManageAppService.disable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/enable")
    @PermissionRegister(code = "system:dept:edit", name = "启用部门", module = "系统管理", desc = "启用部门")
    public Result<Void> enable(@PathVariable Long id) {
        deptManageAppService.enable(id);
        return Result.ok();
    }
}
