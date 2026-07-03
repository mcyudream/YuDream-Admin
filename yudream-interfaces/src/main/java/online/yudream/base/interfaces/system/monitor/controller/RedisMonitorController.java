package online.yudream.base.interfaces.system.monitor.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.monitor.service.RedisMonitorAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.monitor.assembler.MonitorWebAssembler;
import online.yudream.base.interfaces.system.monitor.res.RedisMonitorRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/monitor/redis")
@RequiredArgsConstructor
public class RedisMonitorController {

    private final RedisMonitorAppService redisMonitorAppService;

    @GetMapping
    @PermissionRegister(code = "system:monitor:redis:view", name = "查看Redis监控", module = "系统管理", desc = "查看 Redis 缓存监控")
    public Result<RedisMonitorRes> overview(@RequestParam(value = "pattern", required = false) String pattern,
                                            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return Result.ok(MonitorWebAssembler.toRes(redisMonitorAppService.overview(pattern, limit)));
    }
}
