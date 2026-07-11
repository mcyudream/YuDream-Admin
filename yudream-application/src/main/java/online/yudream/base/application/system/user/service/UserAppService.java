package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.cmd.UserLoginCmd;
import online.yudream.base.application.system.user.cmd.UserPasswordResetCmd;
import online.yudream.base.application.system.user.cmd.UserPasswordResetEmailCmd;
import online.yudream.base.application.system.user.cmd.UserProfileUpdateCmd;
import online.yudream.base.application.system.user.cmd.UserRegisterCmd;
import online.yudream.base.application.system.user.dto.UserProfileDTO;
import online.yudream.base.application.system.user.dto.UserRegisterDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.service.PasswordEncoder;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.enumerate.UserStatus;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.system.user.service.EmailVerifyTokenProvider;
import online.yudream.base.domain.system.user.service.PasswordResetTokenProvider;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.EmailVerifyTarget;
import online.yudream.base.domain.system.user.valobj.PasswordResetTarget;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingCode;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
@Service
public class UserAppService {

    private final UserRepo userRepo;
    private final SettingRepo settingRepo;
    private final PluginQqBindingService pluginQqBindingService;
    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerifyTokenProvider emailVerifyTokenProvider;
    private final PasswordResetTokenProvider passwordResetTokenProvider;
    private final UserRegisterMailSender userRegisterMailSender;
    private final FileAppService fileAppService;

    @Transactional(readOnly = true)
    public User login(UserLoginCmd cmd) {
        User user = findLoginUser(cmd);
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException("用户已停用");
        }
        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Transactional
    public UserRegisterDTO register(UserRegisterCmd cmd) {
        if (userRepo.existsVerifiedByUsername(cmd.getUsername())) {
            throw new BizException("用户名已存在");
        }
        Email email = Email.of(cmd.getEmail());
        if (userRepo.existsVerifiedByEmail(email.getValue())) {
            throw new BizException("邮箱已被注册");
        }

        Dept rootDept = deptRepo.findRoot()
                .orElseThrow(() -> new BizException("系统根部门未初始化"));
        Role userRole = roleRepo.findBySystemType(SystemRoleType.USER)
                .orElseThrow(() -> new BizException("普通用户角色未初始化"));

        User user = User.builder()
                .username(cmd.getUsername())
                .nickname(cmd.getNickname())
                .email(email)
                .password(Password.of(cmd.getPassword(), passwordEncoder))
                .emailVerified(false)
                .build();
        user.joinDept(DeptID.of(rootDept.getId()), true);
        user.assignRoles(RoleID.of(userRole.getId()));

        User saved = userRepo.save(user);

        String token = emailVerifyTokenProvider.generate(saved.getId(), email.getValue());
        userRegisterMailSender.sendVerifyEmail(saved.getUsername(), email.getValue(), token);

        log.info("用户注册成功: id={}, username={}, email={}, deptId={}, roleId={}",
                saved.getId(), saved.getUsername(), email.getValue(), rootDept.getId(), userRole.getId());
        return UserAssembler.toRegisterDTO(saved);
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerifyTarget target = emailVerifyTokenProvider.validate(token)
                .orElseThrow(() -> new BizException("验证链接已过期或无效"));
        User user = resolveEmailVerifyUser(target);
        String email = userEmail(user);
        List<User> sameEmailUsers = userRepo.findByEmailAll(email);
        ensureEmailCanBeVerified(user, sameEmailUsers);
        ensureUsernameCanBeVerified(user);
        user.verifyEmail();
        userRepo.save(user);
        int removedCount = deleteSameEmailUnverifiedUsers(user, sameEmailUsers);
        emailVerifyTokenProvider.remove(token);
        log.info("邮箱验证成功: id={}, email={}, removedUnverified={}", user.getId(), email, removedCount);
    }

    @Transactional
    public void resendVerificationEmail(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (user.isEmailVerified()) {
            throw new BizException("邮箱已验证，无需重复发送");
        }
        if (user.getEmail() == null || !StringUtils.hasText(user.getEmail().getValue())) {
            throw new BizException("当前账户未设置邮箱");
        }
        String email = user.getEmail().getValue();
        String token = emailVerifyTokenProvider.generate(user.getId(), email);
        userRegisterMailSender.sendVerifyEmail(user.getUsername(), email, token);
        log.info("重新发送邮箱验证邮件: id={}, email={}", user.getId(), email);
    }

    @Transactional
    public void sendPasswordResetEmail(UserPasswordResetEmailCmd cmd) {
        String account = cmd == null ? null : cmd.getAccount();
        if (!StringUtils.hasText(account)) {
            return;
        }
        String normalizedAccount = account.trim();
        Optional<User> user = resolvePasswordResetMailUser(normalizedAccount);
        if (user.isEmpty()) {
            log.info("密码重置邮件请求未匹配可重置账号: account={}", normalizedAccount);
            return;
        }
        String email = userEmail(user.get());
        String token = passwordResetTokenProvider.generate(user.get().getId(), email);
        userRegisterMailSender.sendPasswordResetEmail(user.get().getUsername(), email, token);
        log.info("密码重置邮件已发送: id={}, email={}", user.get().getId(), email);
    }

    @Transactional
    public void resetPassword(UserPasswordResetCmd cmd) {
        if (cmd == null || !StringUtils.hasText(cmd.getToken())) {
            throw new BizException("重置链接已过期或无效");
        }
        PasswordResetTarget target = passwordResetTokenProvider.validate(cmd.getToken())
                .orElseThrow(() -> new BizException("重置链接已过期或无效"));
        User user = resolvePasswordResetUser(target);
        user.resetPassword(Password.of(cmd.getPassword(), passwordEncoder));
        userRepo.save(user);
        passwordResetTokenProvider.remove(cmd.getToken());
        log.info("用户密码重置成功: id={}, username={}", user.getId(), user.getUsername());
    }

    @Transactional(readOnly = true)
    public boolean isEmailVerified(Long userId) {
        return userRepo.findById(userId)
                .map(User::isEmailVerified)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public UserProfileDTO profile(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        return toProfileDTO(user);
    }

    @Transactional(readOnly = true)
    public PluginQqBindingCode issueQqBindingCode(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (user.getQq() != null && StringUtils.hasText(user.getQq().getValue())) {
            throw new BizException("当前账号已绑定 QQ，不能重复生成绑定码");
        }
        return pluginQqBindingService.issue(userId);
    }

    @Transactional
    public UserProfileDTO updateProfile(Long userId, UserProfileUpdateCmd cmd) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        String username = normalizeUsername(cmd.getUsername(), false);
        Email email = StringUtils.hasText(cmd.getEmail()) ? Email.of(cmd.getEmail().trim()) : null;
        Phone phone = StringUtils.hasText(cmd.getPhone()) ? Phone.of(cmd.getPhone().trim()) : null;
        QQ qq = StringUtils.hasText(cmd.getQq()) ? QQ.of(cmd.getQq().trim()) : null;

        if (Boolean.parseBoolean(settingRepo.findByKey("plugin.qq-binding.lock-profile-qq").map(online.yudream.base.domain.system.setting.aggregate.Setting::getValue).orElse("false"))
                && !Objects.equals(user.getQq() == null ? null : user.getQq().getValue(), qq == null ? null : qq.getValue())) {
            throw new BizException("当前 QQ 已由群聊绑定管理，不能在个人资料中修改");
        }

        if (StringUtils.hasText(username) && !Objects.equals(username, user.getUsername())) {
            ensureUsernameUnique(username, userId);
            user.changeUsername(username);
        }
        if (email != null && userRepo.existsByEmailExcludeId(email.getValue(), userId)) {
            throw new BizException("邮箱已被使用");
        }
        if (phone != null && userRepo.existsByPhoneExcludeId(phone.getValue(), userId)) {
            throw new BizException("手机号已被使用");
        }
        if (qq != null && userRepo.existsByQQExcludeId(qq.getValue(), userId)) {
            throw new BizException("QQ 已被使用");
        }
        user.updateProfile(cmd.getNickname(), email, phone, qq, null);
        return toProfileDTO(userRepo.save(user));
    }

    @Transactional
    public UserProfileDTO updateAvatar(Long userId, InputStream inputStream, String originalName, String contentType, long size) {
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase().startsWith("image/")) {
            throw new BizException("头像只支持图片文件");
        }
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        FileObjectDTO file = fileAppService.upload(inputStream, originalName, contentType, size, "avatar", userId, true);
        user.updateAvatar(file.getId());
        return toProfileDTO(userRepo.save(user));
    }

    public String avatarUrl(User user) {
        return user == null ? null : fileAppService.fileUrl(user.getAvatarFileId());
    }

    @Transactional
    public User createExternalUser(String nickname) {
        Dept rootDept = deptRepo.findRoot().orElseThrow(() -> new BizException("系统根部门未初始化"));
        Role userRole = roleRepo.findBySystemType(SystemRoleType.USER).orElseThrow(() -> new BizException("普通用户角色未初始化"));
        String base = "external_" + System.currentTimeMillis();
        String username = base;
        int suffix = 1;
        while (userRepo.existsByUsername(username)) {
            username = base + suffix++;
        }
        User user = User.builder().username(username).nickname(StringUtils.hasText(nickname) ? nickname : "第三方用户")
                .password(Password.of(java.util.UUID.randomUUID().toString(), passwordEncoder)).emailVerified(false).build();
        user.joinDept(DeptID.of(rootDept.getId()), true);
        user.assignRoles(RoleID.of(userRole.getId()));
        return userRepo.save(user);
    }

    @Transactional
    public void bindExternalQq(Long userId, String qq) {
        if (!StringUtils.hasText(qq)) return;
        User user = userRepo.findById(userId).orElseThrow(() -> new BizException("用户不存在"));
        if (userRepo.existsByQQExcludeId(qq, userId)) throw new BizException("QQ 已被其他账号绑定");
        user.updateProfile(user.getNickname(), user.getEmail(), user.getPhone(), QQ.of(qq), null);
        userRepo.save(user);
    }

    private User findLoginUser(UserLoginCmd cmd) {
        String account = cmd == null ? null : cmd.getUsername();
        if (!StringUtils.hasText(account)) {
            throw new BizException("用户名或邮箱不能为空");
        }
        return loginCandidates(account.trim()).stream()
                .filter(user -> user.getPassword() != null && user.getPassword().matches(cmd.getPassword(), passwordEncoder))
                .findFirst()
                .orElseThrow(() -> new BizException("用户名、邮箱或密码错误"));
    }

    private List<User> loginCandidates(String account) {
        List<User> users = new ArrayList<>(userRepo.findByUsernameAll(account));
        for (User emailUser : userRepo.findByEmailAll(account)) {
            boolean exists = users.stream().anyMatch(user -> Objects.equals(user.getId(), emailUser.getId()));
            if (!exists) {
                users.add(emailUser);
            }
        }
        return users;
    }

    private User resolveEmailVerifyUser(EmailVerifyTarget target) {
        if (target.userId() != null) {
            User user = userRepo.findById(target.userId())
                    .orElseThrow(() -> new BizException("用户不存在"));
            String currentEmail = userEmail(user);
            if (!currentEmail.equalsIgnoreCase(target.email())) {
                throw new BizException("验证链接已失效，请重新发送验证邮件");
            }
            return user;
        }
        return resolveLegacyEmailVerifyUser(target.email());
    }

    private User resolveLegacyEmailVerifyUser(String email) {
        List<User> users = userRepo.findByEmailAll(email);
        if (users.isEmpty()) {
            throw new BizException("用户不存在");
        }
        List<User> unverifiedUsers = users.stream()
                .filter(user -> !user.isEmailVerified())
                .toList();
        if (unverifiedUsers.size() == 1) {
            return unverifiedUsers.get(0);
        }
        if (users.size() == 1) {
            return users.get(0);
        }
        throw new BizException("验证链接已失效，请重新发送验证邮件");
    }

    private Optional<User> resolvePasswordResetMailUser(String account) {
        Optional<User> byUsername = userRepo.findByUsernameAll(account).stream()
                .filter(this::canSendPasswordResetMail)
                .findFirst();
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return userRepo.findByEmailAll(account).stream()
                .filter(this::canSendPasswordResetMail)
                .findFirst();
    }

    private boolean canSendPasswordResetMail(User user) {
        return user != null
                && user.getStatus() == UserStatus.ACTIVE
                && user.isEmailVerified()
                && user.getEmail() != null
                && StringUtils.hasText(user.getEmail().getValue());
    }

    private User resolvePasswordResetUser(PasswordResetTarget target) {
        if (target.userId() == null || !StringUtils.hasText(target.email())) {
            throw new BizException("重置链接已过期或无效");
        }
        User user = userRepo.findById(target.userId())
                .orElseThrow(() -> new BizException("用户不存在"));
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException("用户已停用");
        }
        if (!user.isEmailVerified()) {
            throw new BizException("邮箱未验证，无法重置密码");
        }
        String currentEmail = userEmail(user);
        if (!currentEmail.equalsIgnoreCase(target.email())) {
            throw new BizException("重置链接已失效，请重新发送重置邮件");
        }
        return user;
    }

    private String userEmail(User user) {
        if (user.getEmail() == null || !StringUtils.hasText(user.getEmail().getValue())) {
            throw new BizException("当前账户未设置邮箱");
        }
        return user.getEmail().getValue();
    }

    private void ensureEmailCanBeVerified(User user, List<User> sameEmailUsers) {
        boolean usedByOtherVerifiedUser = sameEmailUsers.stream()
                .anyMatch(candidate -> !Objects.equals(candidate.getId(), user.getId()) && candidate.isEmailVerified());
        if (usedByOtherVerifiedUser) {
            throw new BizException("邮箱已被验证用户使用");
        }
    }

    private void ensureUsernameCanBeVerified(User user) {
        boolean usedByOtherVerifiedUser = userRepo.findByUsernameAll(user.getUsername()).stream()
                .anyMatch(candidate -> !Objects.equals(candidate.getId(), user.getId()) && candidate.isEmailVerified());
        if (usedByOtherVerifiedUser) {
            throw new BizException("用户名已被验证用户使用");
        }
    }

    private void ensureUsernameUnique(String username, Long excludeId) {
        if (userRepo.existsByUsernameExcludeId(username, excludeId)) {
            throw new BizException("用户名已存在");
        }
    }

    private String normalizeUsername(String username, boolean required) {
        if (!StringUtils.hasText(username)) {
            if (required || username != null) {
                throw new BizException("用户名不能为空");
            }
            return null;
        }
        return username.trim();
    }

    private int deleteSameEmailUnverifiedUsers(User verifiedUser, List<User> sameEmailUsers) {
        List<Long> duplicateIds = sameEmailUsers.stream()
                .filter(candidate -> !Objects.equals(candidate.getId(), verifiedUser.getId()))
                .filter(candidate -> !candidate.isEmailVerified())
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        userRepo.deleteByIds(duplicateIds);
        return duplicateIds.size();
    }

    private UserProfileDTO toProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail() == null ? null : user.getEmail().getValue())
                .phone(user.getPhone() == null ? null : user.getPhone().getValue())
                .qq(user.getQq() == null ? null : user.getQq().getValue())
                .emailVerified(user.isEmailVerified())
                .avatarFileId(user.getAvatarFileId())
                .avatar(avatarUrl(user))
                .createTime(user.getCreateTime())
                .updateTime(user.getUpdateTime())
                .build();
    }

}
