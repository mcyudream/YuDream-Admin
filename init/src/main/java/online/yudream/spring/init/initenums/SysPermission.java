package online.yudream.spring.init.initenums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import online.yudream.spring.entity.entity.Permission;

@Getter
@AllArgsConstructor
public enum SysPermission {
    DEPARTMENT_SELECT_ALL(Permission.builder()
            .id("department.selectAll")
            .name("部门: 获取列表")
            .description("获取全部部门信息")
            .build()),
    DEPARTMENT_SELECT_ONE(Permission.builder()
            .id("department.selectOne")
            .name("部门: 通过ID查询")
            .description("通过id查找部门")
            .build()),
    DEPARTMENT_DELETE(Permission.builder()
            .id("department.delete")
            .name("部门: 删除部门")
            .description("删除部门")
            .build()),
    DEPARTMENT_ADD(Permission.builder()
            .id("department.add")
            .name("部门: 新增部门")
            .description("添加部门")
            .build()),
    DEPARTMENT_UPDATE(Permission.builder()
            .id("department.update")
            .name("部门: 更新部门信息")
            .description("更新部门信息")
            .build()),
    PERMISSION_FIND_BY_ROLE(Permission.builder()
            .id("permission.findByRole")
            .name("权限: 通过角色查找")
            .description("通过角色查找对应信息")
            .build()),
    PERMISSION_SELECT_ALL(Permission.builder()
            .id("permission.selectAll")
            .name("权限: 查找所有权限")
            .description("查找所有权限")
            .build()),
    PERMISSION_ADD(Permission.builder()
            .id("permission.add")
            .name("权限: 新增")
            .description("新增权限")
            .build()),
    PERMISSION_SET(Permission.builder()
            .id("permission.set")
            .name("权限: 设置角色权限")
            .description("设置角色所拥有权限")
            .build()),
    ROLE_SELECT_ALL(Permission.builder()
            .id("role.selectAll")
            .name("角色: 查询全部")
            .description("查询全部角色")
            .build()),
    ROLE_ADD(Permission.builder()
            .id("role.add")
            .name("角色: 新增")
            .description("新增角色")
            .build()),
    ROLE_UPDATE(Permission.builder()
            .id("role.update")
            .name("角色: 更新")
            .description("更新角色信息")
            .build()),
    ROLE_DELETE(Permission.builder()
            .id("role.delete")
            .name("角色: 删除")
            .description("删除角色")
            .build()),
    USER_SELECT_ALL(Permission.builder()
            .id("user.selectAll")
            .name("用户: 查询全部用户")
            .description("查询全部用户")
            .build()),
    USER_UPDATE(Permission.builder()
            .id("user.update")
            .name("用户: 更新")
            .description("更新用户信息")
            .build()),
    USER_DELETE(Permission.builder()
            .id("user.delete")
            .name("用户: 删除")
            .description("删除用户")
            .build())
    ;
    private final Permission permission;
}
