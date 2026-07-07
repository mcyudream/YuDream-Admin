package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.cmd.UserLoginCmd;
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
import online.yudream.base.domain.system.user.service.EmailVerifyTokenProvider;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.EmailVerifyTarget;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import online.yudream.base.domain.valobj.Phone;
import online.yudream.base.domain.valobj.QQ;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@Slf4j
@Service
public class UserAppService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerifyTokenProvider emailVerifyTokenProvider;
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

    @Transactional
    public UserProfileDTO updateProfile(Long userId, UserProfileUpdateCmd cmd) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BizException("用户不存在"));
        Email email = StringUtils.hasText(cmd.getEmail()) ? Email.of(cmd.getEmail().trim()) : null;
        Phone phone = StringUtils.hasText(cmd.getPhone()) ? Phone.of(cmd.getPhone().trim()) : null;
        QQ qq = StringUtils.hasText(cmd.getQq()) ? QQ.of(cmd.getQq().trim()) : null;

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

    private User findLoginUser(UserLoginCmd cmd) {
        return userRepo.findByUsernameAll(cmd.getUsername()).stream()
                .filter(user -> user.getPassword() != null && user.getPassword().matches(cmd.getPassword(), passwordEncoder))
                .findFirst()
                .orElseThrow(() -> new BizException("用户名或密码错误"));
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
