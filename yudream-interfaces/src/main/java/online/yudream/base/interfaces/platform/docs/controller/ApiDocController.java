package online.yudream.base.interfaces.platform.docs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.docs.service.ApiDocAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.docs.assembler.ApiDocWebAssembler;
import online.yudream.base.interfaces.platform.docs.request.ApiDocSettingsUpdateRequest;
import online.yudream.base.interfaces.platform.docs.res.ApiDocSettingsRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/docs")
@RequiredArgsConstructor
public class ApiDocController {

    private final ApiDocAppService apiDocAppService;

    @GetMapping("/settings")
    @PermissionRegister(code = "platform:docs:view", name = "查看API文档", module = "平台能力", desc = "查看 API 文档配置")
    public Result<ApiDocSettingsRes> settings() {
        return Result.ok(ApiDocWebAssembler.toRes(apiDocAppService.settings()));
    }

    @PutMapping("/settings")
    @PermissionRegister(code = "platform:docs:config", name = "配置API文档", module = "平台能力", desc = "配置 API 文档开关与入口")
    public Result<ApiDocSettingsRes> update(@Valid @RequestBody ApiDocSettingsUpdateRequest request) {
        return Result.ok(ApiDocWebAssembler.toRes(apiDocAppService.update(ApiDocWebAssembler.toCmd(request))));
    }
}
