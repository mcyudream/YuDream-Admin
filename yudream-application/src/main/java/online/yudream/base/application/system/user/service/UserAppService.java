package online.yudream.base.application.system.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.user.assembler.UserAssembler;
import online.yudream.base.application.system.user.cmd.UserLoginCmd;
import online.yudream.base.application.system.user.cmd.UserRegisterCmd;
import online.yudream.base.application.system.user.dto.UserRegisterDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.service.PasswordEncoder;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.service.EmailVerifyTokenProvider;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Slf4j
@Service
public class UserAppService {

    private static final Long ROOT_DEPT_ID = 1L;

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final DeptRepo deptRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerifyTokenProvider emailVerifyTokenProvider;
    private final UserRegisterMailSender userRegisterMailSender;

    @Transactional(readOnly = true)
    public User login(UserLoginCmd cmd) {
        User user = userRepo.findByUsername(cmd.getUsername())
                .orElseThrow(() -> new BizException("用户名或密码错误"));
        if (!user.getPassword().matches(cmd.getPassword(), passwordEncoder)) {
            throw new BizException("用户名或密码错误");
        }
        if (!user.isEmailVerified()) {
            throw new BizException("邮箱未验证，请先验证邮箱");
        }
        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Transactional
    public UserRegisterDTO register(UserRegisterCmd cmd) {
        if (userRepo.existsByUsername(cmd.getUsername())) {
            throw new BizException("用户名已存在");
        }
        Email email = Email.of(cmd.getEmail());
        if (userRepo.existsByEmail(email.getValue())) {
            throw new BizException("邮箱已被注册");
        }

        Dept rootDept = deptRepo.findRoot().orElseGet(this::createRootDept);
        Role userRole = roleRepo.findBySystemType(SystemRoleType.USER)
                .orElseGet(() -> createUserRole(rootDept));

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

        String token = emailVerifyTokenProvider.generate(email.getValue());
        userRegisterMailSender.sendVerifyEmail(saved.getUsername(), email.getValue(), token);

        log.info("用户注册成功: id={}, username={}, email={}, deptId={}, roleId={}",
                saved.getId(), saved.getUsername(), email.getValue(), rootDept.getId(), userRole.getId());
        return UserAssembler.toRegisterDTO(saved);
    }

    @Transactional
    public void verifyEmail(String token) {
        String email = emailVerifyTokenProvider.validate(token)
                .orElseThrow(() -> new BizException("验证链接已过期或无效"));
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new BizException("用户不存在"));
        user.verifyEmail();
        userRepo.save(user);
        emailVerifyTokenProvider.remove(token);
        log.info("邮箱验证成功: id={}, email={}", user.getId(), email);
    }

    private Dept createRootDept() {
        Dept root = Dept.create(ROOT_DEPT_ID, "根部门", null, SystemDeptType.ROOT);
        Dept saved = deptRepo.save(root);
        log.info("自动创建系统根部门: id={}", saved.getId());
        return saved;
    }

    private Role createUserRole(Dept rootDept) {
        Role userRole = Role.createSystemRole(SystemRoleType.USER, DeptID.of(rootDept.getId()));
        Role saved = roleRepo.save(userRole);
        log.info("自动创建普通用户角色: id={}", saved.getId());
        return saved;
    }
}
