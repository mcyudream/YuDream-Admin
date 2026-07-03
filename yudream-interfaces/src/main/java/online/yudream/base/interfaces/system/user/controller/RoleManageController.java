package online.yudream.base.interfaces.system.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.query.RolePageQuery;
import online.yudream.base.application.system.user.service.RoleManageAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.user.assembler.UserManageWebAssembler;
import online.yudream.base.interfaces.system.user.request.RoleAssignPermissionsRequest;
import online.yudream.base.interfaces.system.user.request.RoleCreateRequest;
import online.yudream.base.interfaces.system.user.request.RoleUpdateRequest;
import online.yudream.base.interfaces.system.user.res.OptionRes;
import online.yudream.base.interfaces.system.user.res.PermissionRes;
import online.yudream.base.interfaces.system.user.res.RoleManageRes;
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
@RequestMapping("/api/system/roles")
@RequiredArgsConstructor
public class RoleManageController {

    private final RoleManageAppService roleManageAppService;

    @GetMapping
    public Result<PageResult<RoleManageRes>> page(RolePageQuery query) {
        StpUtil.checkLogin();
        return Result.ok(UserManageWebAssembler.toRolePage(roleManageAppService.page(query)));
    }

    @GetMapping("/options")
    public Result<List<OptionRes>> options() {
        StpUtil.checkLogin();
        return Result.ok(UserManageWebAssembler.toOptionResList(roleManageAppService.options()));
    }

    @GetMapping("/permissions")
    public Result<List<PermissionRes>> permissions() {
        StpUtil.checkLogin();
        return Result.ok(UserManageWebAssembler.toPermissionResList(roleManageAppService.permissions()));
    }

    @PostMapping
    @PermissionRegister(code = "system:role:create", name = "新增角色", module = "系统管理", desc = "新增角色")
    public Result<RoleManageRes> create(@Valid @RequestBody RoleCreateRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(roleManageAppService.create(UserManageWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "system:role:edit", name = "编辑角色", module = "系统管理", desc = "编辑角色")
    public Result<RoleManageRes> update(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(roleManageAppService.update(UserManageWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "system:role:delete", name = "删除角色", module = "系统管理", desc = "停用角色")
    public Result<Void> disable(@PathVariable Long id) {
        roleManageAppService.disable(id);
        return Result.ok();
    }

    @PutMapping("/{id}/permissions")
    @PermissionRegister(code = "system:role:edit", name = "分配角色权限", module = "系统管理", desc = "分配角色权限")
    public Result<RoleManageRes> assignPermissions(@PathVariable Long id, @Valid @RequestBody RoleAssignPermissionsRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(roleManageAppService.assignPermissions(id, request.getPermissions())));
    }
}
