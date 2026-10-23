package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.cmd.RoleCreateCmd;
import online.yudream.base.application.system.user.cmd.RoleUpdateCmd;
import online.yudream.base.application.system.user.cmd.TemplateRoleUpsertCmd;
import online.yudream.base.application.system.user.dto.OptionDTO;
import online.yudream.base.application.system.user.dto.PermissionDTO;
import online.yudream.base.application.system.user.dto.RoleManageDTO;
import online.yudream.base.application.system.user.dto.TemplateRoleUpsertResult;
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
import org.springframework.util.StringUtils;

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
        Role role = Role.create(cmd.getName(), cmd.getCode(), DeptID.of(dept.getId()),
                cmd.getLevel() == null ? RoleLevel.USER : cmd.getLevel(), cmd.getIcon(), cmd.getColor());
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
        role.updateAppearance(cmd.getIcon(), cmd.getColor());
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

    /**
     * 模板导入：新建角色。目标是否存在由调用方（模板导入服务）先行判定，
     * 判定不存在后调用本方法；编码冲突沿用「角色编码已存在」规则。
     * 新建角色一律由服务端生成 ID，不写入模板来源 ID；
     * 权限码按现有规则原样入库（失效码不阻断也不丢弃）。
     */
    @Transactional
    public TemplateRoleUpsertResult createFromTemplate(TemplateRoleUpsertCmd cmd) {
        if (!StringUtils.hasText(cmd.getName())) {
            throw new BizException("角色名称不能为空");
        }
        if (!StringUtils.hasText(cmd.getCode())) {
            throw new BizException("角色编码不能为空");
        }
        if (roleRepo.existsByCodeExcludeId(cmd.getCode(), null)) {
            throw new BizException("角色编码已存在");
        }
        if (cmd.getDeptId() == null) {
            throw new BizException("部门不能为空");
        }
        Dept dept = deptRepo.findById(cmd.getDeptId()).orElseThrow(() -> new BizException("部门不存在"));
        Role role = Role.create(cmd.getName(), cmd.getCode(), DeptID.of(dept.getId()),
                cmd.getLevel() == null ? RoleLevel.USER : cmd.getLevel(), cmd.getIcon(), cmd.getColor());
        role.replacePermissions(resolvePermissionIds(cmd.getPermissions()));
        if (cmd.getStatus() == RoleStatus.DEPRECATED) {
            role.deactivate();
        }
        Role saved = roleRepo.save(role);
        return TemplateRoleUpsertResult.builder()
                .created(true)
                .roleId(saved.getId())
                .code(saved.getCode())
                .name(saved.getName())
                .build();
    }

    /**
     * 模板导入：更新既有角色。目标匹配（来源 ID 优先、角色编码兜底）由调用方完成后传入。
     * <p>
     * 受控规则：
     * - 系统角色拒绝模板修改；
     * - 编码不可变：模板编码非空且与现有不一致时抛冲突异常，一致或为空则保持原编码；
     * - 白名单 name/deptId/level/icon/color/permissions/status，模板行缺省（null）字段保持不变；
     *   权限码为 null 或空时保持既有授权不变；
     * - 停用沿用现有安全规则：仍被用户使用的角色不可停用；已停用角色再次要求停用视为幂等通过。
     */
    @Transactional
    public TemplateRoleUpsertResult applyTemplateUpdate(Long roleId, TemplateRoleUpsertCmd cmd) {
        if (roleId == null) {
            throw new BizException("目标角色不能为空");
        }
        Role role = getRole(roleId);
        if (role.isSystemRole()) {
            throw new BizException("系统角色不允许通过模板导入修改");
        }
        if (!StringUtils.hasText(cmd.getName())) {
            throw new BizException("角色名称不能为空");
        }
        if (StringUtils.hasText(cmd.getCode()) && !cmd.getCode().equals(role.getCode())) {
            throw new BizException("角色编码不可修改：模板编码「" + cmd.getCode() + "」与现有编码「" + role.getCode() + "」不一致");
        }
        DeptID deptId = role.getDeptId();
        if (cmd.getDeptId() != null) {
            Dept dept = deptRepo.findById(cmd.getDeptId()).orElseThrow(() -> new BizException("部门不存在"));
            deptId = DeptID.of(dept.getId());
        }
        role.updateBasic(cmd.getName(), role.getCode(), deptId,
                cmd.getLevel() == null ? role.getLevel() : cmd.getLevel());
        role.updateAppearance(
                cmd.isTemplateSnapshot() ? cmd.getIcon() : cmd.getIcon() != null ? cmd.getIcon() : role.getIcon(),
                cmd.isTemplateSnapshot() ? cmd.getColor() : cmd.getColor() != null ? cmd.getColor() : role.getColor());
        if (cmd.isTemplateSnapshot() || cmd.getPermissions() != null) {
            role.replacePermissions(resolvePermissionIds(cmd.getPermissions()));
        }
        if (cmd.getStatus() == RoleStatus.ACTIVE) {
            role.activate();
        } else if (cmd.getStatus() == RoleStatus.DEPRECATED && role.getStatus() != RoleStatus.DEPRECATED) {
            ensureRoleUnused(role.getId());
            role.deactivate();
        }
        Role saved = roleRepo.save(role);
        return TemplateRoleUpsertResult.builder()
                .created(false)
                .roleId(saved.getId())
                .code(saved.getCode())
                .name(saved.getName())
                .build();
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
        // 权限码原样入库：失效/未注册的码不阻断保存也不丢弃，仅在展示层隐藏，权限恢复后授权自动生效
        return permissions.stream().distinct().map(PermissionID::of).toList();
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
                .icon(role.getIcon())
                .color(role.getColor())
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
