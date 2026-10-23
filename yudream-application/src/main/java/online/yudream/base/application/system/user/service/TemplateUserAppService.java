package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.user.cmd.TemplateUserUpsertCmd;
import online.yudream.base.application.system.user.dto.TemplateUserUpsertResult;
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
import online.yudream.base.domain.system.user.service.MessagingIdentityClassifier;
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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 模板导入用户 upsert 应用服务。
 * <p>
 * 供模板导入服务（如 SystemTemplateAppService）在事务内调用：
 * - 目标解析 sourceId 优先：{@code sourceId} 为上一轮导入返回的用户 ID 时按 ID 定位，
 *   username 必须一致（含已验证停用用户）；指向不同用户名的失效映射按 username 解析
 *   兜底，用户名解析无业务键目标时安全失败（BizException），不得新建；
 *   未提供/非数字/指向不存在用户时退回按 {@code username} 解析：已有同用户名账号即更新，否则新建；
 * - 既有用户的 username/password 保持不变（本指令不含密码字段）；
 * - 新建用户的密码由服务端生成占位值（SecureRandom，只落库加密形态，不导出、不返回、不记录）；
 * - 新建用户邮箱可用且状态有效即在结果中提示调用方提交事务后补发初始密码设置邮件
 *   （服务端不导出密码，导入声明 emailVerified=true 也必须完成首设）；
 * - 同名未验证注册残留不算既有用户（与注册/管理端创建口径一致，验证时会被清理）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateUserAppService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;
    private final PasswordEncoder passwordEncoder;
    private final MessagingIdentityAppService messagingIdentityAppService;
    private final UserInitialPasswordAppService userInitialPasswordAppService;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public TemplateUserUpsertResult upsert(TemplateUserUpsertCmd cmd) {
        if (cmd == null) {
            throw new BizException("模板用户指令不能为空");
        }
        String username = normalizeUsername(cmd.getUsername());
        User existing = resolveTarget(cmd, username);
        return existing == null ? createUser(cmd, username) : updateUser(cmd, existing);
    }

    // -------------------- 目标解析 --------------------

    /**
     * 目标解析 sourceId 优先，username 兜底（业务键口径）：
     * <p>
     * - sourceId 为上一轮导入返回的用户 ID（数字字符串）时按 ID 定位；命中的账号
     *   username 必须与模板行一致，一致即为目标（含已验证停用用户，状态尊重导入数据）；
     * - 命中但用户名不一致时视为失效映射，按 username 解析兜底：解析出已验证目标即更新该目标；
     * - sourceId 存在但用户名解析无任何业务键目标（将新建无关账号）时安全失败（BizException）；
     * - sourceId 未提供/非数字/指向不存在用户时退回 username 解析：同用户名账号即为更新目标，
     *   均未命中才新建。
     */
    private User resolveTarget(TemplateUserUpsertCmd cmd, String username) {
        String email = toEmailValue(cmd.getEmail());
        if (!StringUtils.hasText(cmd.getSourceId())) {
            return resolveExistingTemplateUser(username, email);
        }
        Long sourceUserId;
        try {
            sourceUserId = Long.parseLong(cmd.getSourceId().trim());
        }
        catch (NumberFormatException e) {
            return resolveExistingTemplateUser(username, email);
        }
        User mapped = userRepo.findById(sourceUserId).orElse(null);
        if (mapped == null) {
            return resolveExistingTemplateUser(username, email);
        }
        if (username.equals(mapped.getUsername())) {
            return mapped;
        }
        User fallback = resolveExistingTemplateUser(username, email);
        if (fallback != null) {
            return fallback;
        }
        throw new BizException("模板用户ID与用户名均无法匹配目标记录，请人工核对");
    }

    /**
     * 提交事务后为导入的新账号补发初始密码设置邮件。
     */
    public void sendInitialPasswordSetupEmail(Long userId) {
        userInitialPasswordAppService.sendInitialPasswordSetupEmail(userId);
    }

    // -------------------- 新建 --------------------

    private TemplateUserUpsertResult createUser(TemplateUserUpsertCmd cmd, String username) {
        ensureCreateConflicts(cmd, username);
        User user = User.builder()
                .username(username)
                .nickname(cmd.getNickname())
                .email(toEmail(cmd.getEmail()))
                .phone(toPhone(cmd.getPhone()))
                .qq(toQQ(cmd.getQq()))
                .password(placeholderPassword())
                .emailVerified(Boolean.TRUE.equals(cmd.getEmailVerified()))
                .status(cmd.getStatus() == null ? UserStatus.ACTIVE : cmd.getStatus())
                .build();
        boolean useDefaultDept = cmd.getDeptIds() == null || cmd.getDeptIds().isEmpty();
        user.replaceDepts(resolveUserDepts(cmd, useDefaultDept));
        user.replaceRoles(defaultRoleIdsIfNecessary(cmd.getRoleIds(), useDefaultDept));
        ensureRolesBelongToUserDepts(user);
        User saved = userRepo.save(user);
        messagingIdentityAppService.syncMilkyQq(saved.getId(), cmd.getQq());
        deleteSameEmailUnverifiedUsers(saved);
        // 服务端不导出任何密码，新账号即使导入声明 emailVerified=true，
        // 仍需通过初始密码设置邮件完成首次密码设定；停用账号不发。
        boolean needsInitialPasswordEmail = StringUtils.hasText(toEmailValue(cmd.getEmail()))
                && saved.getStatus() == UserStatus.ACTIVE;
        log.info("模板导入新建用户: sourceId={}, id={}, username={}", cmd.getSourceId(), saved.getId(), saved.getUsername());
        return TemplateUserUpsertResult.builder()
                .created(true)
                .userId(saved.getId())
                .username(saved.getUsername())
                .needsInitialPasswordEmail(needsInitialPasswordEmail)
                .build();
    }

    private void ensureCreateConflicts(TemplateUserUpsertCmd cmd, String username) {
        String email = toEmailValue(cmd.getEmail());
        if (!StringUtils.hasText(email)) {
            throw new BizException("新导入用户必须提供邮箱以发送初始密码设置邮件");
        }
        if (userRepo.findByUsernameAll(username).stream().anyMatch(User::isEmailVerified)) {
            throw new BizException("用户名已存在");
        }
        if (userRepo.findByEmailAll(email).stream().anyMatch(User::isEmailVerified)) {
            throw new BizException("邮箱已存在");
        }
        String phone = StringUtils.hasText(cmd.getPhone()) ? cmd.getPhone().trim() : null;
        if (StringUtils.hasText(phone) && userRepo.existsByPhoneExcludeId(phone, null)) {
            throw new BizException("手机号已存在");
        }
        if (StringUtils.hasText(cmd.getQq()) && !MessagingIdentityClassifier.milkyQqNumber(cmd.getQq())) {
            throw new BizException("QQ 号格式无效");
        }
        if (StringUtils.hasText(cmd.getQq()) && userRepo.existsByQQExcludeId(cmd.getQq().trim(), null)) {
            throw new BizException("QQ已存在");
        }
    }

    // -------------------- 更新 --------------------

    private TemplateUserUpsertResult updateUser(TemplateUserUpsertCmd cmd, User user) {
        ensureUpdateConflicts(cmd, user);
        String nickname = cmd.isTemplateSnapshot() || cmd.getNickname() != null ? cmd.getNickname() : user.getNickname();
        Email email = cmd.isTemplateSnapshot() || StringUtils.hasText(cmd.getEmail())
                ? toEmail(cmd.getEmail()) : user.getEmail();
        Phone phone = cmd.isTemplateSnapshot() || StringUtils.hasText(cmd.getPhone())
                ? toPhone(cmd.getPhone()) : user.getPhone();
        QQ qq = cmd.isTemplateSnapshot() || StringUtils.hasText(cmd.getQq())
                ? toQQ(cmd.getQq()) : user.getQq();
        user.updateProfile(nickname, email, phone, qq, cmd.getEmailVerified());
        applyStatus(cmd, user);
        boolean deptsProvided = cmd.isTemplateSnapshot() || (cmd.getDeptIds() != null && !cmd.getDeptIds().isEmpty());
        boolean rolesProvided = cmd.isTemplateSnapshot() || (cmd.getRoleIds() != null && !cmd.getRoleIds().isEmpty());
        if (deptsProvided) {
            user.replaceDepts(cmd.isTemplateSnapshot() && (cmd.getDeptIds() == null || cmd.getDeptIds().isEmpty())
                    ? new ArrayList<>() : resolveUserDepts(cmd, false));
            if (!rolesProvided) {
                pruneRolesOutsideUserDepts(user);
            }
        }
        if (rolesProvided) {
            user.replaceRoles(resolveRoleIds(cmd.getRoleIds()));
        }
        ensureRolesBelongToUserDepts(user);
        User saved = userRepo.save(user);
        messagingIdentityAppService.syncMilkyQq(user.getId(), qq == null ? null : qq.getValue());
        deleteSameEmailUnverifiedUsers(saved);
        log.info("模板导入更新用户: sourceId={}, id={}, username={}", cmd.getSourceId(), user.getId(), user.getUsername());
        return TemplateUserUpsertResult.builder()
                .created(false)
                .userId(user.getId())
                .username(user.getUsername())
                .needsInitialPasswordEmail(false)
                .build();
    }

    private void ensureUpdateConflicts(TemplateUserUpsertCmd cmd, User user) {
        String email = toEmailValue(cmd.getEmail());
        if (StringUtils.hasText(email) && userRepo.existsByEmailExcludeId(email, user.getId())) {
            throw new BizException("邮箱已被使用");
        }
        String phone = StringUtils.hasText(cmd.getPhone()) ? cmd.getPhone().trim() : null;
        if (StringUtils.hasText(phone) && userRepo.existsByPhoneExcludeId(phone, user.getId())) {
            throw new BizException("手机号已被使用");
        }
        if (StringUtils.hasText(cmd.getQq()) && !MessagingIdentityClassifier.milkyQqNumber(cmd.getQq())) {
            throw new BizException("QQ 号格式无效");
        }
        if (StringUtils.hasText(cmd.getQq()) && userRepo.existsByQQExcludeId(cmd.getQq().trim(), user.getId())) {
            throw new BizException("QQ 已被使用");
        }
    }

    private void applyStatus(TemplateUserUpsertCmd cmd, User user) {
        if (cmd.getStatus() == null || cmd.getStatus() == user.getStatus()) {
            return;
        }
        if (cmd.getStatus() == UserStatus.ACTIVE) {
            user.activate();
        } else {
            user.disable();
        }
    }

    // -------------------- 通用解析 --------------------

    /** 稳定用户名匹配：优先已验证账号，随后匹配同用户名的导入残留，保证无源 ID 的重试幂等。 */
    private User resolveExistingTemplateUser(String username, String expectedEmail) {
        List<User> candidates = userRepo.findByUsernameAll(username);
        User verified = candidates.stream().filter(User::isEmailVerified).findFirst().orElse(null);
        return verified != null ? verified : candidates.stream().findFirst().orElse(null);
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BizException("用户名不能为空");
        }
        return username.trim();
    }

    /** 服务端占位密码：只用 SecureRandom 黑盒生成并立即加密，不保留明文、不返回、不记录。 */
    private Password placeholderPassword() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Password.fromEncoded(passwordEncoder.encode(HexFormat.of().formatHex(bytes)));
    }

    private List<UserDept> resolveUserDepts(TemplateUserUpsertCmd cmd, boolean useDefaultDept) {
        if (useDefaultDept) {
            Dept rootDept = deptRepo.findRoot()
                    .orElseThrow(() -> new BizException("系统根部门未初始化"));
            return List.of(new UserDept(DeptID.of(rootDept.getId()), true));
        }
        List<Long> deptIds = distinct(cmd.getDeptIds());
        List<Dept> depts = deptRepo.findByIds(deptIds);
        if (depts.size() != deptIds.size()) {
            throw new BizException("部门不存在");
        }
        Long defaultDeptId = resolveDefaultDeptId(cmd, deptIds);
        return deptIds.stream()
                .map(id -> new UserDept(DeptID.of(id), Objects.equals(id, defaultDeptId)))
                .toList();
    }

    private Long resolveDefaultDeptId(TemplateUserUpsertCmd cmd, List<Long> deptIds) {
        if (cmd.getDefaultDeptId() != null) {
            if (!deptIds.contains(cmd.getDefaultDeptId())) {
                throw new BizException("默认部门必须属于已分配部门");
            }
            return cmd.getDefaultDeptId();
        }
        return deptIds.getFirst();
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

    private List<RoleID> resolveRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = distinct(roleIds);
        List<Role> roles = roleRepo.findByIds(ids);
        if (roles.size() != ids.size()) {
            throw new BizException("角色不存在");
        }
        if (roles.stream().anyMatch(role -> role.getStatus() != RoleStatus.ACTIVE)) {
            throw new BizException("角色已停用");
        }
        return ids.stream().map(RoleID::of).toList();
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

    private void deleteSameEmailUnverifiedUsers(User verifiedUser) {
        if (verifiedUser == null || !verifiedUser.isEmailVerified()
                || verifiedUser.getEmail() == null || !StringUtils.hasText(verifiedUser.getEmail().getValue())) {
            return;
        }
        List<Long> duplicateIds = userRepo.findByEmailAll(verifiedUser.getEmail().getValue()).stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), verifiedUser.getId()))
                .filter(candidate -> !candidate.isEmailVerified())
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        userRepo.deleteByIds(duplicateIds);
    }

    private List<Long> distinct(List<Long> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private String toEmailValue(String email) {
        return StringUtils.hasText(email) ? email.trim() : null;
    }

    private Email toEmail(String email) {
        return StringUtils.hasText(email) ? Email.of(email.trim()) : null;
    }

    private Phone toPhone(String phone) {
        return StringUtils.hasText(phone) ? Phone.of(phone.trim()) : null;
    }

    private QQ toQQ(String qq) {
        return StringUtils.hasText(qq) ? QQ.of(qq.trim()) : null;
    }
}
