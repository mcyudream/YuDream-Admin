package online.yudream.base.interfaces.system.monitor.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.monitor.service.SystemMonitorAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.monitor.assembler.MonitorWebAssembler;
import online.yudream.base.interfaces.system.monitor.res.ApiLogRes;
import online.yudream.base.interfaces.system.monitor.res.LoginLogRes;
import online.yudream.base.interfaces.system.monitor.res.OnlineUserRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/monitor")
@RequiredArgsConstructor
public class SystemMonitorController {

    private final SystemMonitorAppService systemMonitorAppService;

    @GetMapping("/online-users")
    @PermissionRegister(code = "system:monitor:online:view", name = "查看在线用户", module = "系统管理", desc = "查看在线用户会话")
    public Result<List<OnlineUserRes>> onlineUsers(@RequestParam(value = "keyword", required = false) String keyword,
                                                   @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return Result.ok(MonitorWebAssembler.toOnlineUserResList(systemMonitorAppService.onlineUsers(keyword, limit)));
    }

    @DeleteMapping("/online-users/{token}")
    @PermissionRegister(code = "system:monitor:online:kickout", name = "强制用户下线", module = "系统管理", desc = "强制下线在线用户会话")
    public Result<Void> kickout(@PathVariable String token) {
        systemMonitorAppService.kickout(token);
        return Result.ok();
    }

    @GetMapping("/api-logs")
    @PermissionRegister(code = "system:monitor:api-log:view", name = "查看接口日志", module = "系统管理", desc = "查看系统接口日志")
    public Result<PageResult<ApiLogRes>> apiLogs(@RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "success", required = false) Boolean success,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "10") int size) {
        return Result.ok(MonitorWebAssembler.toApiLogPage(systemMonitorAppService.pageApiLogs(keyword, success, page, size)));
    }

    @GetMapping("/login-logs")
    @PermissionRegister(code = "system:monitor:login-log:view", name = "查看登录日志", module = "系统管理", desc = "查看系统登录日志")
    public Result<PageResult<LoginLogRes>> loginLogs(@RequestParam(value = "keyword", required = false) String keyword,
                                                     @RequestParam(value = "success", required = false) Boolean success,
                                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                                     @RequestParam(value = "size", defaultValue = "10") int size) {
        return Result.ok(MonitorWebAssembler.toLoginLogPage(systemMonitorAppService.pageLoginLogs(keyword, success, page, size)));
    }
}
