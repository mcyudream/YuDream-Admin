package online.yudream.base.interfaces.system.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.dto.LoginTokenDTO;
import online.yudream.base.application.system.security.service.LoginTokenAppService;
import online.yudream.base.application.system.user.query.UserPageQuery;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.application.system.user.service.UserManageAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.user.assembler.UserManageWebAssembler;
import online.yudream.base.interfaces.system.user.assembler.UserWebAssembler;
import online.yudream.base.interfaces.system.user.request.UserAssignDeptsRequest;
import online.yudream.base.interfaces.system.user.request.UserAssignRolesRequest;
import online.yudream.base.interfaces.system.user.request.UserCreateRequest;
import online.yudream.base.interfaces.system.user.request.UserUpdateRequest;
import online.yudream.base.interfaces.system.user.res.UserLoginRes;
import online.yudream.base.interfaces.system.user.res.UserManageRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/users")
@RequiredArgsConstructor
public class UserManageController {

    private final UserManageAppService userManageAppService;
    private final UserAppService userAppService;
    private final LoginTokenAppService loginTokenAppService;

    @GetMapping
    public Result<PageResult<UserManageRes>> page(UserPageQuery query) {
        StpUtil.checkLogin();
        return Result.ok(UserManageWebAssembler.toUserPage(userManageAppService.page(query)));
    }

    @PostMapping
    @PermissionRegister(code = "system:user:create", name = "新增用户", module = "系统管理", desc = "新增后台用户")
    public Result<UserManageRes> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(userManageAppService.create(UserManageWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "system:user:edit", name = "编辑用户", module = "系统管理", desc = "编辑用户资料")
    public Result<UserManageRes> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(userManageAppService.update(UserManageWebAssembler.toCmd(id, request))));
    }

    @DeleteMapping("/{id}")
    @PermissionRegister(code = "system:user:delete", name = "删除用户", module = "系统管理", desc = "停用用户")
    public Result<Void> disable(@PathVariable Long id) {
        userManageAppService.disable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/enable")
    @PermissionRegister(code = "system:user:edit", name = "编辑用户", module = "系统管理", desc = "编辑用户资料")
    public Result<Void> enable(@PathVariable Long id) {
        userManageAppService.enable(id);
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @PermissionRegister(code = "system:user:assign-role", name = "分配用户角色", module = "系统管理", desc = "分配用户角色")
    public Result<UserManageRes> assignRoles(@PathVariable Long id, @Valid @RequestBody UserAssignRolesRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(userManageAppService.assignRoles(id, request.getRoleIds())));
    }

    @PutMapping("/{id}/depts")
    @PermissionRegister(code = "system:user:assign-dept", name = "分配用户部门", module = "系统管理", desc = "分配用户部门")
    public Result<UserManageRes> assignDepts(@PathVariable Long id, @Valid @RequestBody UserAssignDeptsRequest request) {
        return Result.ok(UserManageWebAssembler.toRes(userManageAppService.assignDepts(id, UserManageWebAssembler.toDeptAssignCmds(request))));
    }

    @PostMapping("/{id}/impersonate")
    @PermissionRegister(code = "system:user:impersonate", name = "伪装用户", module = "系统管理", desc = "伪装用户访问系统")
    public Result<UserLoginRes> impersonate(@PathVariable Long id) {
        User user = userManageAppService.getImpersonationTarget(StpUtil.getLoginIdAsLong(), id);
        LoginTokenDTO token = loginTokenAppService.issueForLogin(user.getId());
        return Result.ok(UserWebAssembler.toLoginRes(user, token, userAppService.avatarUrl(user)));
    }
}
