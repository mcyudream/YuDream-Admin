package online.yudream.base.application.system.setting.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.setting.cmd.SetupCmd;
import online.yudream.base.application.system.setting.dto.SetupStatusDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.common.service.PasswordEncoder;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.system.user.aggregate.Dept;
import online.yudream.base.domain.system.user.aggregate.Role;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.enumerate.SystemDeptType;
import online.yudream.base.domain.system.user.enumerate.SystemRoleType;
import online.yudream.base.domain.system.user.repo.DeptRepo;
import online.yudream.base.domain.system.user.repo.RoleRepo;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.domain.system.user.valobj.DeptID;
import online.yudream.base.domain.system.user.valobj.RoleID;
import online.yudream.base.domain.valobj.Email;
import online.yudream.base.domain.valobj.Password;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统初始化应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupAppService {

    private static final String SETUP_COMPLETED_KEY = "system.setup.completed";
    private static final String SITE_NAME_KEY = "site.name";

    private final SettingRepo settingRepo;
    private final UserRepo userRepo;
    private final DeptRepo deptRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public SetupStatusDTO isSetupRequired() {
        boolean completed = settingRepo.existsByKey(SETUP_COMPLETED_KEY);
        return SetupStatusDTO.builder().setupCompleted(completed).build();
    }

    @Transactional
    public User initialize(SetupCmd cmd) {
        if (settingRepo.existsByKey(SETUP_COMPLETED_KEY)) {
            throw new BizException("系统已初始化，请勿重复操作");
        }
        validate(cmd);

        Dept systemAdminDept = deptRepo.findByType(SystemDeptType.SYSTEM_ADMIN)
                .orElseThrow(() -> new BizException("系统管理部门未初始化"));
        Role superAdminRole = roleRepo.findBySystemType(SystemRoleType.SUPER_ADMIN)
                .orElseThrow(() -> new BizException("超级管理员角色未初始化"));

        User user = User.builder()
                .username(cmd.getAdminUsername())
                .nickname(StringUtils.hasText(cmd.getAdminNickname()) ? cmd.getAdminNickname() : cmd.getAdminUsername())
                .email(Email.of(cmd.getAdminEmail()))
                .password(Password.of(cmd.getAdminPassword(), passwordEncoder))
                .emailVerified(true)
                .build();
        user.joinDept(DeptID.of(systemAdminDept.getId()), true);
        user.assignRoles(RoleID.of(superAdminRole.getId()));

        User saved = userRepo.save(user);

        settingRepo.save(Setting.builder()
                .key(SITE_NAME_KEY)
                .value(cmd.getSiteName())
                .type(SettingType.STRING)
                .category("site")
                .description("站点名称")
                .build());
        settingRepo.save(Setting.builder()
                .key(SETUP_COMPLETED_KEY)
                .value("true")
                .type(SettingType.BOOLEAN)
                .category("system")
                .description("系统是否已完成初始化")
                .build());

        log.info("系统初始化完成: adminId={}, username={}, siteName={}", saved.getId(), saved.getUsername(), cmd.getSiteName());
        return saved;
    }

    private void validate(SetupCmd cmd) {
        if (!StringUtils.hasText(cmd.getSiteName())) {
            throw new BizException("站点名称不能为空");
        }
        if (!StringUtils.hasText(cmd.getAdminUsername())) {
            throw new BizException("管理员用户名不能为空");
        }
        if (!StringUtils.hasText(cmd.getAdminEmail())) {
            throw new BizException("管理员邮箱不能为空");
        }
        if (!StringUtils.hasText(cmd.getAdminPassword())) {
            throw new BizException("管理员密码不能为空");
        }
        if (!cmd.getAdminPassword().equals(cmd.getAdminConfirmPassword())) {
            throw new BizException("两次输入的密码不一致");
        }
        if (cmd.getAdminUsername().length() < 3) {
            throw new BizException("管理员用户名至少3位");
        }
        if (userRepo.existsByUsername(cmd.getAdminUsername())) {
            throw new BizException("用户名已存在");
        }
        if (userRepo.existsByEmail(cmd.getAdminEmail())) {
            throw new BizException("邮箱已存在");
        }
    }
}
