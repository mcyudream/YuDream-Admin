package online.yudream.base.interfaces.system.setting.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.setting.service.SettingAppService;
import online.yudream.base.interfaces.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统设置公开接口。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingAppService settingAppService;

    @GetMapping("/public")
    public Result<Map<String, String>> publicSettings() {
        return Result.ok(settingAppService.publicSettings());
    }
}
