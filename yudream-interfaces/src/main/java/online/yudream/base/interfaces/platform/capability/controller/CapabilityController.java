package online.yudream.base.interfaces.platform.capability.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.capability.assembler.CapabilityWebAssembler;
import online.yudream.base.interfaces.platform.capability.request.CapabilityConfigUpdateRequest;
import online.yudream.base.interfaces.platform.capability.request.CapabilityTestRequest;
import online.yudream.base.interfaces.platform.capability.res.CapabilityRes;
import online.yudream.base.interfaces.platform.capability.res.CapabilityTestRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/capabilities")
@RequiredArgsConstructor
public class CapabilityController {

    private final CapabilityAppService capabilityAppService;

    @GetMapping
    @PermissionRegister(code = "platform:capability:view", name = "查看能力管理", module = "平台能力", desc = "查看平台能力模块列表与运行状态")
    public Result<List<CapabilityRes>> list() {
        return Result.ok(CapabilityWebAssembler.toResList(capabilityAppService.list()));
    }

    @PutMapping("/{code}/config")
    @PermissionRegister(code = "platform:capability:config", name = "配置平台能力", module = "平台能力", desc = "修改平台能力模块配置")
    public Result<CapabilityRes> updateConfig(@PathVariable String code, @RequestBody CapabilityConfigUpdateRequest request) {
        return Result.ok(CapabilityWebAssembler.toRes(capabilityAppService.updateConfig(CapabilityWebAssembler.toCmd(code, request))));
    }

    @PostMapping("/{code}/enable")
    @PermissionRegister(code = "platform:capability:enable", name = "启用平台能力", module = "平台能力", desc = "启用平台能力模块")
    public Result<CapabilityRes> enable(@PathVariable String code) {
        return Result.ok(CapabilityWebAssembler.toRes(capabilityAppService.enable(code)));
    }

    @PostMapping("/{code}/disable")
    @PermissionRegister(code = "platform:capability:disable", name = "禁用平台能力", module = "平台能力", desc = "禁用平台能力模块")
    public Result<CapabilityRes> disable(@PathVariable String code) {
        return Result.ok(CapabilityWebAssembler.toRes(capabilityAppService.disable(code)));
    }

    @PostMapping("/{code}/test")
    @PermissionRegister(code = "platform:capability:test", name = "测试平台能力", module = "平台能力", desc = "发送平台能力测试消息")
    public Result<CapabilityTestRes> test(@PathVariable String code, @RequestBody CapabilityTestRequest request) {
        return Result.ok(CapabilityWebAssembler.toRes(capabilityAppService.test(CapabilityWebAssembler.toCmd(code, request))));
    }
}
