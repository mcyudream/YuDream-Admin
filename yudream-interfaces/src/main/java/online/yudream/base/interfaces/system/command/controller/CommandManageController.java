package online.yudream.base.interfaces.system.command.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.command.service.CommandManageAppService;
import online.yudream.base.domain.platform.plugin.valobj.PluginCommandInfo;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import online.yudream.base.plugin.spi.system.user.PluginQqBindingCode;

import java.util.List;

@RestController
@RequestMapping("/api/system/commands")
@RequiredArgsConstructor
public class CommandManageController {
    private final CommandManageAppService commandManageAppService;

    @GetMapping
    @PermissionRegister(code = "system:command:view", name = "查看指令", module = "系统管理", desc = "查看已注册消息指令")
    public Result<List<PluginCommandInfo>> list() {
        return Result.ok(commandManageAppService.list());
    }

    @GetMapping("/qq-binding-policy")
    public Result<java.util.Map<String, Boolean>> qqBindingPolicy() { return Result.ok(commandManageAppService.qqBindingPolicy()); }

    @PutMapping("/qq-binding-policy")
    @PermissionRegister(code = "system:command:edit", name = "配置指令", module = "系统管理", desc = "配置 QQ 绑定指令策略")
    public Result<java.util.Map<String, Boolean>> updateQqBindingPolicy(@RequestParam boolean requireBoundQq) {
        return Result.ok(commandManageAppService.updateQqBindingPolicy(requireBoundQq));
    }

    @org.springframework.web.bind.annotation.PostMapping("/qq-binding-codes")
    @PermissionRegister(code = "system:command:edit", name = "生成 QQ 绑定码", module = "系统管理", desc = "为指定用户生成一次性 QQ 绑定码")
    public Result<PluginQqBindingCode> issueQqBindingCode(@RequestParam Long userId) {
        return Result.ok(commandManageAppService.issueQqBindingCode(userId));
    }
}
