package online.yudream.base.interfaces.platform.satori.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.satori.service.SatoriConnectionAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.satori.assembler.SatoriConnectionWebAssembler;
import online.yudream.base.interfaces.platform.satori.request.SatoriConnectionCreateRequest;
import online.yudream.base.interfaces.platform.satori.request.SatoriConnectionUpdateRequest;
import online.yudream.base.interfaces.platform.satori.res.SatoriConnectionRes;
import online.yudream.base.interfaces.platform.satori.res.SatoriConnectionTestRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/satori/connections")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriConnectionController {
    private final SatoriConnectionAppService connectionAppService;

    @GetMapping
    @PermissionRegister(code = "platform:satori:view", name = "查看 Satori 连接", module = "Satori 平台", desc = "查看 Satori 连接列表")
    public Result<PageResult<SatoriConnectionRes>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.page(SatoriConnectionWebAssembler.toQuery(keyword, page, size))));
    }

    @PostMapping
    @PermissionRegister(code = "platform:satori:config", name = "配置 Satori 连接", module = "Satori 平台", desc = "创建 Satori 连接")
    public Result<SatoriConnectionRes> create(@Valid @RequestBody SatoriConnectionCreateRequest request) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.create(SatoriConnectionWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:satori:config", name = "配置 Satori 连接", module = "Satori 平台", desc = "更新 Satori 连接")
    public Result<SatoriConnectionRes> update(@PathVariable Long id, @Valid @RequestBody SatoriConnectionUpdateRequest request) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.update(SatoriConnectionWebAssembler.toCmd(id, request))));
    }

    @PostMapping("/{id}/enable")
    @PermissionRegister(code = "platform:satori:connect", name = "启用 Satori 连接", module = "Satori 平台", desc = "启用 Satori 连接")
    public Result<SatoriConnectionRes> enable(@PathVariable Long id) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.enable(id)));
    }

    @PostMapping("/{id}/disable")
    @PermissionRegister(code = "platform:satori:connect", name = "停用 Satori 连接", module = "Satori 平台", desc = "停用 Satori 连接")
    public Result<SatoriConnectionRes> disable(@PathVariable Long id) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.disable(id)));
    }

    @PostMapping("/{id}/test")
    @PermissionRegister(code = "platform:satori:connect", name = "测试 Satori 连接", module = "Satori 平台", desc = "请求 Satori Meta 验证连接")
    public Result<SatoriConnectionTestRes> test(@PathVariable Long id) {
        return Result.ok(SatoriConnectionWebAssembler.toRes(connectionAppService.test(id)));
    }
}
