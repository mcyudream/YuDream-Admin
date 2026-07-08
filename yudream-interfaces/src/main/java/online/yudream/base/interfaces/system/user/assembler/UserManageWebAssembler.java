package online.yudream.base.interfaces.system.user.assembler;

import online.yudream.base.application.system.user.cmd.DeptCreateCmd;
import online.yudream.base.application.system.user.cmd.DeptUpdateCmd;
import online.yudream.base.application.system.user.cmd.RoleCreateCmd;
import online.yudream.base.application.system.user.cmd.RoleUpdateCmd;
import online.yudream.base.application.system.user.cmd.UserCreateCmd;
import online.yudream.base.application.system.user.cmd.UserDeptAssignCmd;
import online.yudream.base.application.system.user.cmd.UserUpdateCmd;
import online.yudream.base.application.system.user.dto.DeptManageDTO;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.dto.PermissionDTO;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.system.user.request.DeptCreateRequest;
import online.yudream.base.interfaces.system.user.request.DeptUpdateRequest;
import online.yudream.base.interfaces.system.user.request.RoleCreateRequest;
import online.yudream.base.interfaces.system.user.request.RoleUpdateRequest;
import online.yudream.base.interfaces.system.user.request.UserAssignDeptsRequest;
import online.yudream.base.interfaces.system.user.request.UserCreateRequest;
import online.yudream.base.interfaces.system.user.request.UserDeptAssignRequest;
import online.yudream.base.interfaces.system.user.request.UserUpdateRequest;
import online.yudream.base.interfaces.system.user.res.DeptManageRes;
import online.yudream.base.interfaces.system.user.res.OptionRes;
import online.yudream.base.interfaces.system.user.res.PermissionRes;
import online.yudream.base.interfaces.system.user.res.RoleManageRes;
import online.yudream.base.interfaces.system.user.res.UserManageRes;

import java.util.List;

public class UserManageWebAssembler {

    private UserManageWebAssembler() {
    }

    public static UserCreateCmd toCmd(UserCreateRequest request) {
        UserCreateCmd cmd = new UserCreateCmd();
        cmd.setUsername(request.getUsername());
        cmd.setNickname(request.getNickname());
        cmd.setEmail(request.getEmail());
        cmd.setPhone(request.getPhone());
        cmd.setQq(request.getQq());
        cmd.setPassword(request.getPassword());
        cmd.setEmailVerified(request.isEmailVerified());
        cmd.setRoleIds(request.getRoleIds());
        cmd.setDepts(request.getDepts().stream().map(UserManageWebAssembler::toCmd).toList());
        return cmd;
    }

    public static UserUpdateCmd toCmd(Long id, UserUpdateRequest request) {
        UserUpdateCmd cmd = new UserUpdateCmd();
        cmd.setId(id);
        cmd.setUsername(request.getUsername());
        cmd.setNickname(request.getNickname());
        cmd.setEmail(request.getEmail());
        cmd.setPhone(request.getPhone());
        cmd.setQq(request.getQq());
        cmd.setEmailVerified(request.getEmailVerified());
        return cmd;
    }

    public static UserDeptAssignCmd toCmd(UserDeptAssignRequest request) {
        UserDeptAssignCmd cmd = new UserDeptAssignCmd();
        cmd.setDeptId(request.getDeptId());
        cmd.setDefaultDept(request.isDefaultDept());
        return cmd;
    }

    public static List<UserDeptAssignCmd> toDeptAssignCmds(UserAssignDeptsRequest request) {
        return request.getDepts().stream().map(UserManageWebAssembler::toCmd).toList();
    }

    public static RoleCreateCmd toCmd(RoleCreateRequest request) {
        RoleCreateCmd cmd = new RoleCreateCmd();
        cmd.setName(request.getName());
        cmd.setCode(request.getCode());
        cmd.setDeptId(request.getDeptId());
        cmd.setLevel(request.getLevel());
        cmd.setPermissions(request.getPermissions());
        return cmd;
    }

    public static RoleUpdateCmd toCmd(Long id, RoleUpdateRequest request) {
        RoleUpdateCmd cmd = new RoleUpdateCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setCode(request.getCode());
        cmd.setDeptId(request.getDeptId());
        cmd.setLevel(request.getLevel());
        cmd.setStatus(request.getStatus());
        cmd.setPermissions(request.getPermissions());
        return cmd;
    }

    public static DeptCreateCmd toCmd(DeptCreateRequest request) {
        DeptCreateCmd cmd = new DeptCreateCmd();
        cmd.setName(request.getName());
        cmd.setDescription(request.getDescription());
        cmd.setLeaderId(request.getLeaderId());
        cmd.setPhone(request.getPhone());
        cmd.setParentId(request.getParentId());
        cmd.setSortOrder(request.getSortOrder());
        return cmd;
    }

    public static DeptUpdateCmd toCmd(Long id, DeptUpdateRequest request) {
        DeptUpdateCmd cmd = new DeptUpdateCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setDescription(request.getDescription());
        cmd.setLeaderId(request.getLeaderId());
        cmd.setPhone(request.getPhone());
        cmd.setParentId(request.getParentId());
        cmd.setSortOrder(request.getSortOrder());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static PageResult<UserManageRes> toUserPage(PageResult<UserManageDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(UserManageWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<RoleManageRes> toRolePage(PageResult<RoleManageDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(UserManageWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static List<DeptManageRes> toDeptResList(List<DeptManageDTO> items) {
        return items == null ? List.of() : items.stream().map(UserManageWebAssembler::toRes).toList();
    }

    public static List<OptionRes> toOptionResList(List<OptionDTO> items) {
        return items == null ? List.of() : items.stream().map(UserManageWebAssembler::toRes).toList();
    }

    public static List<PermissionRes> toPermissionResList(List<PermissionDTO> items) {
        return items == null ? List.of() : items.stream().map(UserManageWebAssembler::toRes).toList();
    }

    public static UserManageRes toRes(UserManageDTO dto) {
        return UserManageRes.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .nickname(dto.getNickname())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .qq(dto.getQq())
                .emailVerified(dto.isEmailVerified())
                .status(dto.getStatus())
                .roleIds(dto.getRoleIds())
                .roleNames(dto.getRoleNames())
                .deptIds(dto.getDeptIds())
                .deptNames(dto.getDeptNames())
                .defaultDeptId(dto.getDefaultDeptId())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static RoleManageRes toRes(RoleManageDTO dto) {
        return RoleManageRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .deptId(dto.getDeptId())
                .deptName(dto.getDeptName())
                .code(dto.getCode())
                .level(dto.getLevel())
                .systemRole(dto.isSystemRole())
                .systemType(dto.getSystemType())
                .permissions(dto.getPermissions())
                .permissionCount(dto.getPermissionCount())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static DeptManageRes toRes(DeptManageDTO dto) {
        return DeptManageRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .leaderId(dto.getLeaderId())
                .leaderName(dto.getLeaderName())
                .phone(dto.getPhone())
                .parentId(dto.getParentId())
                .sortOrder(dto.getSortOrder())
                .deptType(dto.getDeptType())
                .status(dto.getStatus())
                .systemDept(dto.isSystemDept())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .children(toDeptResList(dto.getChildren()))
                .build();
    }

    public static OptionRes toRes(OptionDTO dto) {
        return OptionRes.builder()
                .id(dto.getId())
                .label(dto.getLabel())
                .value(dto.getValue())
                .deptId(dto.getDeptId())
                .deptName(dto.getDeptName())
                .build();
    }

    public static PermissionRes toRes(PermissionDTO dto) {
        return PermissionRes.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .module(dto.getModule())
                .desc(dto.getDesc())
                .status(dto.getStatus())
                .build();
    }
}
