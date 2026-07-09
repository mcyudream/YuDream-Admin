package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.dto.DeptManageDTO;
import online.yudream.base.application.system.user.cmd.UserCreateCmd;
import online.yudream.base.application.system.user.cmd.UserProfileUpdateCmd;
import online.yudream.base.application.system.user.dto.UserDeptVO;
import online.yudream.base.application.system.user.dto.UserManageDTO;
import online.yudream.base.application.system.user.dto.UserProfileDTO;
import online.yudream.base.application.system.user.dto.UserRoleVO;
import online.yudream.base.application.system.user.query.DeptTreeQuery;
import online.yudream.base.application.system.user.query.UserPageQuery;
import online.yudream.base.application.system.user.service.DeptManageAppService;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.application.system.user.service.UserContextAppService;
import online.yudream.base.application.system.user.service.UserManageAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.service.PasswordEncoder;
import online.yudream.base.domain.system.user.enumerate.DeptStatus;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.plugin.spi.system.user.PluginDeptOption;
import online.yudream.base.plugin.spi.system.user.PluginUserDept;
import online.yudream.base.plugin.spi.system.user.PluginUserOption;
import online.yudream.base.plugin.spi.system.user.PluginUserCreate;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserProfileUpdate;
import online.yudream.base.plugin.spi.system.user.PluginUserRole;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PluginUserFrameworkService implements PluginUserService {

    private final UserRepo userRepo;
    private final UserAppService userAppService;
    private final UserContextAppService userContextAppService;
    private final UserManageAppService userManageAppService;
    private final DeptManageAppService deptManageAppService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Optional<PluginUserProfile> authenticate(String usernameOrEmail, String password) {
        if (!hasText(usernameOrEmail) || !hasText(password)) {
            return Optional.empty();
        }
        String account = usernameOrEmail.trim();
        Optional<User> user = userRepo.findByUsername(account);
        if (user.isEmpty() && account.contains("@")) {
            user = userRepo.findByEmail(account);
        }
        if (user.isEmpty() || user.get().getPassword() == null) {
            return Optional.empty();
        }
        User current = user.get();
        if (current.getStatus() == UserStatus.DISABLED
                || !current.isEmailVerified()
                || !current.getPassword().matches(password, passwordEncoder)) {
            return Optional.empty();
        }
        return Optional.of(toProfile(current));
    }

    @Override
    public PluginUserProfile create(PluginUserCreate create) {
        if (create == null) {
            throw new BizException("用户创建参数不能为空");
        }
        UserCreateCmd cmd = new UserCreateCmd();
        cmd.setUsername(create.username());
        cmd.setNickname(create.nickname());
        cmd.setEmail(create.email());
        cmd.setPhone(create.phone());
        cmd.setQq(create.qq());
        cmd.setPassword(create.password());
        cmd.setEncodedPassword(normalizeBcryptHash(create.encodedPassword()));
        cmd.setEmailVerified(create.emailVerified());
        UserManageDTO user = userManageAppService.create(cmd);
        return new PluginUserProfile(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getQq(),
                null,
                user.getStatus() == null ? null : user.getStatus().name()
        );
    }

    @Override
    public Optional<PluginUserProfile> findById(Long userId) {
        return userRepo.findById(userId).map(this::toProfile);
    }

    @Override
    public Optional<PluginUserProfile> findByUsername(String username) {
        return userRepo.findByUsername(username).map(this::toProfile);
    }

    @Override
    public Optional<PluginUserProfile> findByEmail(String email) {
        return userRepo.findByEmail(email).map(this::toProfile);
    }

    @Override
    public List<PluginUserOption> searchUsers(String keyword, Long deptId, int page, int size) {
        Map<String, PluginUserOption> result = new LinkedHashMap<>();
        String safeKeyword = trimToNull(keyword);
        if (isDigits(safeKeyword)) {
            userRepo.findById(Long.parseLong(safeKeyword))
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .map(this::toUserOption)
                    .filter(user -> deptId == null || user.deptIds().contains(String.valueOf(deptId)))
                    .ifPresent(user -> result.put(user.id(), user));
        }
        UserPageQuery query = new UserPageQuery();
        query.setKeyword(safeKeyword);
        query.setDeptId(deptId);
        query.setStatus(UserStatus.ACTIVE);
        query.setPage(Math.max(page, 1));
        query.setSize(Math.max(Math.min(size <= 0 ? 20 : size, 200), 1));
        userManageAppService.page(query).getRecords().stream()
                .map(this::toUserOption)
                .forEach(user -> result.putIfAbsent(user.id(), user));
        return List.copyOf(result.values());
    }

    @Override
    public List<PluginDeptOption> listDepartments(String keyword) {
        DeptTreeQuery query = new DeptTreeQuery();
        query.setKeyword(trimToNull(keyword));
        query.setStatus(DeptStatus.ACTIVE);
        return deptManageAppService.tree(query).stream().map(this::toDeptOption).toList();
    }

    @Override
    public List<PluginUserRole> listRoles(Long userId) {
        return userContextAppService.listRoles(userId).stream().map(this::toRole).toList();
    }

    @Override
    public List<PluginUserDept> listDepartments(Long userId) {
        return userContextAppService.listDepts(userId).stream().map(this::toDept).toList();
    }

    @Override
    public void updateProfile(Long userId, PluginUserProfileUpdate update) {
        if (update == null) {
            throw new BizException("用户资料不能为空");
        }
        UserProfileUpdateCmd cmd = new UserProfileUpdateCmd();
        cmd.setNickname(update.nickname());
        cmd.setEmail(update.email());
        cmd.setPhone(update.phone());
        cmd.setQq(update.qq());
        userAppService.updateProfile(userId, cmd);
    }

    private PluginUserProfile toProfile(User user) {
        UserProfileDTO dto = userAppService.profile(user.getId());
        return new PluginUserProfile(
                dto.getId(),
                dto.getUsername(),
                dto.getNickname(),
                dto.getEmail(),
                dto.getPhone(),
                dto.getQq(),
                dto.getAvatar(),
                user.getStatus() == null ? null : user.getStatus().name()
        );
    }

    private PluginUserOption toUserOption(UserManageDTO user) {
        return new PluginUserOption(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                null,
                user.getStatus() == null ? null : user.getStatus().name(),
                user.getDeptIds() == null ? List.of() : user.getDeptIds().stream().map(String::valueOf).toList(),
                user.getDeptNames() == null ? List.of() : user.getDeptNames()
        );
    }

    private PluginUserOption toUserOption(User user) {
        PluginUserProfile profile = toProfile(user);
        List<PluginUserDept> depts = listDepartments(user.getId());
        return new PluginUserOption(
                String.valueOf(user.getId()),
                profile.username(),
                profile.nickname(),
                profile.email(),
                profile.avatar(),
                user.getStatus() == null ? null : user.getStatus().name(),
                depts.stream().map(dept -> String.valueOf(dept.id())).toList(),
                depts.stream().map(PluginUserDept::name).toList()
        );
    }

    private PluginDeptOption toDeptOption(DeptManageDTO dept) {
        return new PluginDeptOption(
                String.valueOf(dept.getId()),
                dept.getName(),
                dept.getParentId() == null ? null : String.valueOf(dept.getParentId()),
                dept.getStatus() == null ? null : dept.getStatus().name(),
                dept.getChildren() == null ? List.of() : dept.getChildren().stream().map(this::toDeptOption).toList()
        );
    }

    private PluginUserRole toRole(UserRoleVO role) {
        return new PluginUserRole(role.getId(), role.getCode(), role.getName());
    }

    private PluginUserDept toDept(UserDeptVO dept) {
        return new PluginUserDept(dept.getId(), dept.getName(), dept.isDefaultDept());
    }

    private String normalizeBcryptHash(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            return encodedPassword;
        }
        String trimmed = encodedPassword.trim();
        return trimmed.startsWith("$2y$") ? "$2a$" + trimmed.substring(4) : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isDigits(String value) {
        return value != null && !value.isBlank() && value.chars().allMatch(Character::isDigit);
    }
}
