package online.yudream.base.application.system.setting.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.setting.aggregate.Setting;
import online.yudream.base.domain.system.setting.repo.SettingRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置查询应用服务。
 */
@Service
@RequiredArgsConstructor
public class SettingAppService {

    private final SettingRepo settingRepo;

    @Transactional(readOnly = true)
    public Map<String, String> publicSettings() {
        List<Setting> settings = settingRepo.findAll();
        Map<String, String> map = new HashMap<>();
        for (Setting setting : settings) {
            map.put(setting.getKey(), setting.getValue());
        }
        return map;
    }
}
