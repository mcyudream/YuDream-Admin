package online.yudream.base.interfaces.system.setting.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.setting.dto.SetupStatusDTO;
import online.yudream.base.application.system.setting.service.SetupAppService;
import online.yudream.base.domain.system.user.aggregate.User;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.setting.assembler.SetupWebAssembler;
import online.yudream.base.interfaces.system.setting.request.SetupRequest;
import online.yudream.base.interfaces.system.setting.res.SetupStatusRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统初始化接口。
 */
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
public class SetupController {

    private final SetupAppService setupAppService;

    @GetMapping("/status")
    public Result<SetupStatusRes> status() {
        SetupStatusDTO dto = setupAppService.isSetupRequired();
        return Result.ok(SetupStatusRes.builder()
                .setupCompleted(dto.isSetupCompleted())
                .build());
    }

    @PostMapping("/init")
    public Result<Void> init(@Valid @RequestBody SetupRequest request) {
        User user = setupAppService.initialize(SetupWebAssembler.toCmd(request));
        return Result.ok();
    }
}
