package online.yudream.base.interfaces.platform.milky.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.service.MilkyConnectionAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.milky.assembler.MilkyConnectionWebAssembler;
import online.yudream.base.interfaces.platform.milky.request.MilkyConnectionCreateRequest;
import online.yudream.base.interfaces.platform.milky.request.MilkyConnectionUpdateRequest;
import online.yudream.base.interfaces.platform.milky.res.MilkyConnectionRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/milky/connections")
@RequiredArgsConstructor
public class MilkyConnectionController {
    private final MilkyConnectionAppService appService;

    @GetMapping
    @PermissionRegister(code = "platform:milky:view", name = "查看 Milky 连接", module = "Milky", desc = "查看连接")
    public Result<PageResult<MilkyConnectionRes>> page(@RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return Result.ok(MilkyConnectionWebAssembler.toRes(appService.page(keyword, page, size)));
    }

    @PostMapping
    @PermissionRegister(code = "platform:milky:config", name = "配置 Milky 连接", module = "Milky", desc = "创建连接")
    public Result<MilkyConnectionRes> create(@Valid @RequestBody MilkyConnectionCreateRequest request) {
        return Result.ok(MilkyConnectionWebAssembler.toRes(appService.create(MilkyConnectionWebAssembler.toCmd(request))));
    }

    @PutMapping("/{id}")
    @PermissionRegister(code = "platform:milky:config", name = "配置 Milky 连接", module = "Milky", desc = "更新连接")
    public Result<MilkyConnectionRes> update(@PathVariable Long id, @Valid @RequestBody MilkyConnectionUpdateRequest request) {
        return Result.ok(MilkyConnectionWebAssembler.toRes(appService.update(MilkyConnectionWebAssembler.toCmd(id, request))));
    }

    @PostMapping("/{id}/enable")
    @PermissionRegister(code = "platform:milky:connect", name = "启用 Milky 连接", module = "Milky", desc = "启用连接")
    public Result<MilkyConnectionRes> enable(@PathVariable Long id) {
        return Result.ok(MilkyConnectionWebAssembler.toRes(appService.enable(id)));
    }

    @PostMapping("/{id}/disable")
    @PermissionRegister(code = "platform:milky:connect", name = "停用 Milky 连接", module = "Milky", desc = "停用连接")
    public Result<MilkyConnectionRes> disable(@PathVariable Long id) {
        return Result.ok(MilkyConnectionWebAssembler.toRes(appService.disable(id)));
    }

    @PostMapping("/{id}/test")
    @PermissionRegister(code = "platform:milky:connect", name = "测试 Milky 连接", module = "Milky", desc = "测试连接")
    public Result<Object> test(@PathVariable Long id) {
        return Result.ok(appService.test(id));
    }
}
