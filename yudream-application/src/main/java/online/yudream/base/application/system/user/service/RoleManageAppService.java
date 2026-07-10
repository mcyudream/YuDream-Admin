package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.cmd.RoleCreateCmd;
import online.yudream.base.application.system.user.cmd.RoleUpdateCmd;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.dto.PermissionDTO;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.query.RolePageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Permission;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.enumerate.PermissionStatus;
import online.yudream.base.domain.system.user.enumerate.RoleLevel;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.PermissionRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.PermissionID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleManageAppService {

    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;
    private final PermissionRepo permissionRepo;
    private final UserRepo userRepo;

    @Transactional(readOnly = true)
    public PageResult<RoleManageDTO> page(RolePageQuery query) {
        PageResult<Role> page = roleRepo.page(query.getKeyword(), query.getDeptId(), query.getStatus(), query.getPage(), query.getSize());
        return new PageResult<>(toDTOs(page.getRecords()), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional(readOnly = true)
    public List<OptionDTO> options() {
        List<Role> roles = roleRepo.findAll().stream()
                .filter(role -> role.getStatus() == RoleStatus.ACTIVE)
                .toList();
        Map<Long, Dept> deptMap = deptRepo.findByIds(roles.stream()
                        .map(Role::getDeptId)
                        .filter(Objects::nonNull)
                        .map(DeptID::getValue)
                        .distinct()
                        .toList())
                .stream().collect(Collectors.toMap(Dept::getId, Function.identity()));
        return roles.stream()
                .map(role -> OptionDTO.builder()
                        .id(role.getId())
                        .label(role.getName())
                        .value(String.valueOf(role.getId()))
                        .deptId(role.getDeptId() == null ? null : role.getDeptId().getValue())
                        .deptName(role.getDeptId() == null || deptMap.get(role.getDeptId().getValue()) == null
                                ? null
                                : deptMap.get(role.getDeptId().getValue()).getName())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionDTO> permissions() {
        return permissionRepo.findActive().stream().map(this::toPermissionDTO).toList();
    }

    @Transactional
    public RoleManageDTO create(RoleCreateCmd cmd) {
        if (roleRepo.existsByCodeExcludeId(cmd.getCode(), null)) {
            throw new BizException("角色编码已存在");
        }
        Dept dept = deptRepo.findById(cmd.getDeptId()).orElseThrow(() -> new BizException("部门不存在"));
        Role role = Role.create(cmd.getName(), cmd.getCode(), DeptID.of(dept.getId()), cmd.getLevel() == null ? RoleLevel.USER : cmd.getLevel());
        role.replacePermissions(resolvePermissionIds(cmd.getPermissions()));
        return toDTO(roleRepo.save(role));
    }

    @Transactional
    public RoleManageDTO update(RoleUpdateCmd cmd) {
        Role role = getRole(cmd.getId());
        if (roleRepo.existsByCodeExcludeId(cmd.getCode(), role.getId())) {
            throw new BizException("角色编码已存在");
        }
        Dept dept = deptRepo.findById(cmd.getDeptId()).orElseThrow(() -> new BizException("部门不存在"));
        role.updateBasic(cmd.getName(), cmd.getCode(), DeptID.of(dept.getId()), cmd.getLevel() == null ? role.getLevel() : cmd.getLevel());
        role.replacePermissions(resolvePermissionIds(cmd.getPermissions()));
        if (cmd.getStatus() == RoleStatus.ACTIVE) {
            role.activate();
        } else if (cmd.getStatus() == RoleStatus.DEPRECATED) {
            ensureRoleUnused(role.getId());
            role.deactivate();
        }
        return toDTO(roleRepo.save(role));
    }

    @Transactional
    public void disable(Long id) {
        Role role = getRole(id);
        ensureRoleUnused(id);
        role.deactivate();
        roleRepo.save(role);
    }

    @Transactional
    public void enable(Long id) {
        Role role = getRole(id);
        role.activate();
        roleRepo.save(role);
    }

    @Transactional
    public RoleManageDTO assignPermissions(Long id, List<String> permissions) {
        Role role = getRole(id);
        role.replacePermissions(resolvePermissionIds(permissions));
        return toDTO(roleRepo.save(role));
    }

    private Role getRole(Long id) {
        return roleRepo.findById(id).orElseThrow(() -> new BizException("角色不存在"));
    }

    private void ensureRoleUnused(Long id) {
        if (userRepo.countByRoleId(id) > 0) {
            throw new BizException("角色仍被用户使用，不能停用");
        }
    }

    private List<PermissionID> resolvePermissionIds(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> activeCodes = permissionRepo.findActive().stream()
                .map(permission -> permission.getId().getCode())
                .collect(Collectors.toSet());
        List<String> distinct = permissions.stream().distinct().toList();
        if (!activeCodes.containsAll(distinct)) {
            throw new BizException("权限不存在或已废弃");
        }
        return distinct.stream().map(PermissionID::of).toList();
    }

    private List<RoleManageDTO> toDTOs(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Dept> deptMap = deptRepo.findByIds(roles.stream()
                        .map(Role::getDeptId)
                        .filter(Objects::nonNull)
                        .map(DeptID::getValue)
                        .distinct()
                        .toList())
                .stream().collect(Collectors.toMap(Dept::getId, Function.identity()));
        return roles.stream().map(role -> toDTO(role, deptMap)).toList();
    }

    private RoleManageDTO toDTO(Role role) {
        Map<Long, Dept> deptMap = role.getDeptId() == null ? Map.of() : deptRepo.findByIds(List.of(role.getDeptId().getValue())).stream()
                .collect(Collectors.toMap(Dept::getId, Function.identity()));
        return toDTO(role, deptMap);
    }

    private RoleManageDTO toDTO(Role role, Map<Long, Dept> deptMap) {
        Dept dept = role.getDeptId() == null ? null : deptMap.get(role.getDeptId().getValue());
        return RoleManageDTO.builder()
                .id(role.getId())
                .name(role.getName())
                .deptId(role.getDeptId() == null ? null : role.getDeptId().getValue())
                .deptName(dept == null ? null : dept.getName())
                .code(role.getCode())
                .level(role.getLevel())
                .systemRole(role.isSystemRole())
                .systemType(role.getSystemType())
                .permissions(role.getPermissions() == null ? new ArrayList<>() : role.getPermissions().stream().map(PermissionID::getCode).toList())
                .permissionCount(role.getPermissions() == null ? 0 : role.getPermissions().size())
                .status(role.getStatus())
                .createTime(role.getCreateTime())
                .updateTime(role.getUpdateTime())
                .build();
    }

    private PermissionDTO toPermissionDTO(Permission permission) {
        return PermissionDTO.builder()
                .code(permission.getId().getCode())
                .name(permission.getName())
                .module(permission.getModule())
                .desc(permission.getDescription())
                .status(permission.getStatus() == null ? PermissionStatus.ACTIVE : permission.getStatus())
                .build();
    }
}
