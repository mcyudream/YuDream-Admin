package online.yudream.base.application.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.cmd.UserProfileUpdateCmd;
import online.yudream.base.application.system.user.dto.UserDeptVO;
import online.yudream.base.application.system.user.dto.UserProfileDTO;
import online.yudream.base.application.system.user.dto.UserRoleVO;
import online.yudream.base.application.system.user.service.UserAppService;
import online.yudream.base.application.system.user.service.UserContextAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.plugin.spi.system.user.PluginUserDept;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.spi.system.user.PluginUserProfileUpdate;
import online.yudream.base.plugin.spi.system.user.PluginUserRole;
import online.yudream.base.plugin.spi.system.user.PluginUserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PluginUserFrameworkService implements PluginUserService {

    private final UserRepo userRepo;
    private final UserAppService userAppService;
    private final UserContextAppService userContextAppService;

    @Override
    public Optional<PluginUserProfile> findById(Long userId) {
        return userRepo.findById(userId).map(this::toProfile);
    }

    @Override
    public Optional<PluginUserProfile> findByUsername(String username) {
        return userRepo.findByUsername(username).map(this::toProfile);
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

    private PluginUserRole toRole(UserRoleVO role) {
        return new PluginUserRole(role.getId(), role.getCode(), role.getName());
    }

    private PluginUserDept toDept(UserDeptVO dept) {
        return new PluginUserDept(dept.getId(), dept.getName(), dept.isDefaultDept());
    }
}
