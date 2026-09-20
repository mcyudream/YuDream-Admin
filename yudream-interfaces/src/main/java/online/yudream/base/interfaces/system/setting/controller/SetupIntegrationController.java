package online.yudream.base.interfaces.system.setting.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.setting.dto.IntegrationConfigDTO;
import online.yudream.base.application.system.setting.service.IntegrationConfigAppService;
import online.yudream.base.domain.system.integration.valobj.IntegrationProbeResult;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.setting.assembler.IntegrationConfigWebAssembler;
import online.yudream.base.interfaces.system.setting.request.IntegrationConfigSaveRequest;
import online.yudream.base.interfaces.system.setting.request.MailTestRequest;
import online.yudream.base.interfaces.system.setting.request.StorageTestRequest;
import online.yudream.base.interfaces.system.setting.res.IntegrationConfigRes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 安装向导集成配置接口（匿名，仅限初始化未完成的窗口期）：
 * 可选配置邮件/对象存储，测试通过后落库；初始化完成后接口自动失效。
 */
@RestController
@RequestMapping("/api/setup/integrations")
@RequiredArgsConstructor
public class SetupIntegrationController {

    private final IntegrationConfigAppService integrationConfigAppService;

    @PostMapping("/test-mail")
    public Result<IntegrationProbeResult> testMail(@RequestBody MailTestRequest request) {
        integrationConfigAppService.ensureSetupWindow(request.getSetupToken());
        return Result.ok(integrationConfigAppService.testMail(IntegrationConfigWebAssembler.toCmd(request)));
    }

    @PostMapping("/test-storage")
    public Result<IntegrationProbeResult> testStorage(@RequestBody StorageTestRequest request) {
        integrationConfigAppService.ensureSetupWindow(request.getSetupToken());
        return Result.ok(integrationConfigAppService.testStorage(IntegrationConfigWebAssembler.toCmd(request)));
    }

    @PutMapping
    public Result<IntegrationConfigRes> save(@RequestBody IntegrationConfigSaveRequest request) {
        integrationConfigAppService.ensureSetupWindow(request.getSetupToken());
        IntegrationConfigDTO dto = integrationConfigAppService.update(
                IntegrationConfigWebAssembler.toCmd(request));
        return Result.ok(IntegrationConfigWebAssembler.toRes(dto));
    }
}
