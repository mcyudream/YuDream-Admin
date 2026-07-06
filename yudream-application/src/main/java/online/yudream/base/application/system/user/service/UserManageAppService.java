package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.cmd.UserCreateCmd;
import online.yudream.base.application.system.user.cmd.UserDeptAssignCmd;
import online.yudream.base.application.system.user.cmd.UserUpdateCmd;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.application.system.user.query.UserPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.service.PasswordEncoder;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.RoleStatus;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.system.user.valobj.UserDept;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManageAppService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public PageResult<UserManageDTO> page(UserPageQuery query) {
        PageResult<User> page = userRepo.page(
                query.getKeyword(),
                query.getDeptId(),
                query.getRoleId(),
                query.getEmailVerified(),
                query.getStatus(),
                query.getPage(),
                query.getSize()
        );
        return new PageResult<>(toDTOs(page.getRecords()), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional
    public UserManageDTO create(UserCreateCmd cmd) {
        ensureUserUnique(null, cmd.getUsername(), cmd.getEmail(), cmd.getPhone(), cmd.getQq());
        User user = User.builder()
                .username(cmd.getUsername())
                .nickname(cmd.getNickname())
                .email(toEmail(cmd.getEmail()))
                .phone(toPhone(cmd.getPhone()))
                .qq(toQQ(cmd.getQq()))
                .password(toPassword(cmd))
                .emailVerified(cmd.isEmailVerified())
                .build();
        boolean useDefaultDept = cmd.getDepts() == null || cmd.getDepts().isEmpty();
        user.replaceDepts(useDefaultDept ? defaultUserDepts() : resolveUserDepts(cmd.getDepts()));
        user.replaceRoles(defaultRoleIdsIfNecessary(cmd.getRoleIds(), useDefaultDept));
        ensureRolesBelongToUserDepts(user);
        return toDTO(userRepo.save(user));
    }

    @Transactional
    public UserManageDTO update(UserUpdateCmd cmd) {
        User user = getUser(cmd.getId());
        ensureUserUnique(user.getId(), user.getUsername(), cmd.getEmail(), cmd.getPhone(), cmd.getQq());
        user.updateProfile(cmd.getNickname(), toEmail(cmd.getEmail()), toPhone(cmd.getPhone()), toQQ(cmd.getQq()), cmd.getEmailVerified());
        return toDTO(userRepo.save(user));
    }

    @Transactional
    public void disable(Long id) {
        User user = getUser(id);
        user.disable();
        userRepo.save(user);
    }

    @Transactional
    public UserManageDTO assignRoles(Long userId, List<Long> roleIds) {
        User user = getUser(userId);
        user.replaceRoles(resolveRoleIds(roleIds));
        ensureRolesBelongToUserDepts(user);
        return toDTO(userRepo.save(user));
    }

    @Transactional
    public UserManageDTO assignDepts(Long userId, List<UserDeptAssignCmd> depts) {
        User user = getUser(userId);
        user.replaceDepts(resolveUserDepts(depts));
        pruneRolesOutsideUserDepts(user);
        return toDTO(userRepo.save(user));
    }

    @Transactional(readOnly = true)
    public User getImpersonationTarget(Long operatorId, Long targetUserId) {
        if (operatorId != null && operatorId.equals(targetUserId)) {
            throw new BizException("不能伪装自己");
        }
        User user = getUser(targetUserId);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException("用户已停用");
        }
        return user;
    }

    private User getUser(Long id) {
        return userRepo.findById(id).orElseThrow(() -> new BizException("用户不存在"));
    }

    private void ensureUserUnique(Long excludeId, String username, String email, String phone, String qq) {
        if (excludeId == null && userRepo.existsByUsername(username)) {
            throw new BizException("用户名已存在");
        }
        if (excludeId != null && userRepo.existsByUsernameExcludeId(username, excludeId)) {
            throw new BizException("用户名已存在");
        }
        if (StringUtils.hasText(email) && userRepo.existsByEmailExcludeId(email, excludeId)) {
            throw new BizException("邮箱已存在");
        }
        if (StringUtils.hasText(phone) && userRepo.existsByPhoneExcludeId(phone, excludeId)) {
            throw new BizException("手机号已存在");
        }
        if (StringUtils.hasText(qq) && userRepo.existsByQQExcludeId(qq, excludeId)) {
            throw new BizException("QQ已存在");
        }
    }

    private List<RoleID> resolveRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Role> roles = roleRepo.findByIds(roleIds);
        if (roles.size() != roleIds.stream().distinct().count()) {
            throw new BizException("角色不存在");
        }
        if (roles.stream().anyMatch(role -> role.getStatus() != RoleStatus.ACTIVE)) {
            throw new BizException("角色已停用");
        }
        return roleIds.stream().distinct().map(RoleID::of).toList();
    }

    private List<RoleID> defaultRoleIdsIfNecessary(List<Long> roleIds, boolean useDefaultDept) {
        List<RoleID> resolved = resolveRoleIds(roleIds);
        if (!resolved.isEmpty() || !useDefaultDept) {
            return resolved;
        }
        Role userRole = roleRepo.findBySystemType(SystemRoleType.USER)
                .orElseThrow(() -> new BizException("普通用户角色未初始化"));
        return List.of(RoleID.of(userRole.getId()));
    }

    private void ensureRolesBelongToUserDepts(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return;
        }
        List<Long> userDeptIds = user.getDepts() == null
                ? List.of()
                : user.getDepts().stream().map(dept -> dept.id().getValue()).distinct().toList();
        if (userDeptIds.isEmpty()) {
            throw new BizException("请先分配用户部门");
        }
        List<Long> roleIds = user.getRoles().stream().map(RoleID::getValue).distinct().toList();
        List<Role> roles = roleRepo.findByIds(roleIds);
        boolean invalid = roles.size() != roleIds.size() || roles.stream()
                .anyMatch(role -> role.getDeptId() == null || !userDeptIds.contains(role.getDeptId().getValue()));
        if (invalid) {
            throw new BizException("用户角色必须属于已分配部门");
        }
    }

    private void pruneRolesOutsideUserDepts(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return;
        }
        List<Long> userDeptIds = user.getDepts() == null
                ? List.of()
                : user.getDepts().stream().map(dept -> dept.id().getValue()).distinct().toList();
        List<Role> roles = roleRepo.findByIds(user.getRoles().stream().map(RoleID::getValue).distinct().toList());
        List<RoleID> validRoleIds = roles.stream()
                .filter(role -> role.getDeptId() != null && userDeptIds.contains(role.getDeptId().getValue()))
                .map(role -> RoleID.of(role.getId()))
                .toList();
        user.replaceRoles(validRoleIds);
    }

    private List<UserDept> resolveUserDepts(List<UserDeptAssignCmd> depts) {
        if (depts == null || depts.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> deptIds = depts.stream().map(UserDeptAssignCmd::getDeptId).distinct().toList();
        List<Dept> existing = deptRepo.findByIds(deptIds);
        if (existing.size() != deptIds.size()) {
            throw new BizException("部门不存在");
        }
        return depts.stream()
                .map(d -> new UserDept(DeptID.of(d.getDeptId()), d.isDefaultDept()))
                .toList();
    }

    private List<UserDept> defaultUserDepts() {
        Dept rootDept = deptRepo.findRoot()
                .orElseThrow(() -> new BizException("系统根部门未初始化"));
        return List.of(new UserDept(DeptID.of(rootDept.getId()), true));
    }

    private List<UserManageDTO> toDTOs(List<User> users) {
        if (users == null || users.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Role> roleMap = roleRepo.findByIds(users.stream()
                        .flatMap(user -> user.getRoles().stream())
                        .map(RoleID::getValue)
                        .distinct()
                        .toList())
                .stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        List<Long> deptIds = users.stream()
                .flatMap(user -> user.getDepts().stream())
                .map(dept -> dept.id().getValue())
                .distinct()
                .toList();
        Map<Long, Dept> deptMap = deptRepo.findByIds(deptIds).stream()
                .collect(Collectors.toMap(Dept::getId, Function.identity()));
        return users.stream().map(user -> UserAssembler.toManageDTO(user, roleMap, deptMap)).toList();
    }

    private UserManageDTO toDTO(User user) {
        Map<Long, Role> roleMap = roleRepo.findByIds(user.getRoles().stream().map(RoleID::getValue).toList())
                .stream().collect(Collectors.toMap(Role::getId, Function.identity()));
        List<Long> deptIds = user.getDepts().stream().map(d -> d.id().getValue()).toList();
        Map<Long, Dept> deptMap = deptRepo.findByIds(deptIds).stream()
                .collect(Collectors.toMap(Dept::getId, Function.identity()));
        return UserAssembler.toManageDTO(user, roleMap, deptMap);
    }

    private Email toEmail(String email) {
        return StringUtils.hasText(email) ? Email.of(email) : null;
    }

    private Password toPassword(UserCreateCmd cmd) {
        if (StringUtils.hasText(cmd.getEncodedPassword())) {
            return Password.fromEncoded(cmd.getEncodedPassword().trim());
        }
        return Password.of(cmd.getPassword(), passwordEncoder);
    }

    private Phone toPhone(String phone) {
        return StringUtils.hasText(phone) ? Phone.of(phone) : null;
    }

    private QQ toQQ(String qq) {
        return StringUtils.hasText(qq) ? QQ.of(qq) : null;
    }
}
