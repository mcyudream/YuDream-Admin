package online.yudream.base.application.system.command.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.plugin.service.PluginRuntimeGateway;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.enumerate.SettingType;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.domain.system.user.repo.UserRepo;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingCode;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommandManageAppService {
    private final PluginRuntimeGateway pluginRuntimeGateway;
    private final SettingRepo settingRepo;
    private final UserRepo userRepo;
    private final PluginQqBindingService pluginQqBindingService;
    private static final String REQUIRE_BOUND_QQ = "plugin.qq-binding.require-bound-qq";
    private static final String LOCK_PROFILE_QQ = "plugin.qq-binding.lock-profile-qq";

    public List<PluginCommandInfo> list() {
        return pluginRuntimeGateway.commands();
    }

    public java.util.Map<String, Boolean> qqBindingPolicy() {
        return java.util.Map.of("requireBoundQq", value(REQUIRE_BOUND_QQ), "lockProfileQq", value(LOCK_PROFILE_QQ));
    }

    @org.springframework.transaction.annotation.Transactional
    public java.util.Map<String, Boolean> updateQqBindingPolicy(boolean requireBoundQq) {
        save(REQUIRE_BOUND_QQ, requireBoundQq, "未绑定 QQ 不允许执行系统指令");
        save(LOCK_PROFILE_QQ, requireBoundQq, "QQ 绑定后禁止个人资料修改");
        return qqBindingPolicy();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PluginQqBindingCode issueQqBindingCode(Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new online.yudream.base.domain.common.exception.BizException("用户不存在"));
        if (user.getQq() != null && org.springframework.util.StringUtils.hasText(user.getQq().getValue())) {
            throw new online.yudream.base.domain.common.exception.BizException("该用户已绑定 QQ，不能重复生成绑定码");
        }
        return pluginQqBindingService.issue(userId);
    }

    private boolean value(String key) { return settingRepo.findByKey(key).map(Setting::getValue).map(Boolean::parseBoolean).orElse(false); }
    private void save(String key, boolean value, String description) {
        Setting setting = settingRepo.findByKey(key).orElseGet(() -> Setting.builder().key(key).category("qq-binding").type(SettingType.STRING).description(description).build());
        setting.setValue(String.valueOf(value)); settingRepo.save(setting);
    }
}
