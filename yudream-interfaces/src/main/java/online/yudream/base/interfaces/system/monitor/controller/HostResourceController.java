package online.yudream.base.interfaces.system.monitor.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.monitor.service.HostResourceAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.monitor.assembler.MonitorWebAssembler;
import online.yudream.base.interfaces.system.monitor.res.HostResourceSnapshotRes;
import online.yudream.base.interfaces.system.monitor.res.ResourceMetricPointRes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/monitor/resource")
@RequiredArgsConstructor
public class HostResourceController {

    private final HostResourceAppService hostResourceAppService;

    @GetMapping
    @PermissionRegister(code = "system:monitor:resource:view", name = "查看资源监控", module = "系统管理", desc = "查看主机与 JVM 资源监控")
    public Result<HostResourceSnapshotRes> snapshot() {
        return Result.ok(MonitorWebAssembler.toRes(hostResourceAppService.snapshot()));
    }

    @GetMapping("/history")
    @PermissionRegister(code = "system:monitor:resource:view", name = "查看资源监控", module = "系统管理", desc = "查看主机与 JVM 资源监控")
    public Result<List<ResourceMetricPointRes>> history(@RequestParam(value = "hours", defaultValue = "1") int hours) {
        return Result.ok(MonitorWebAssembler.toResourceHistoryRes(hostResourceAppService.history(hours)));
    }
}
