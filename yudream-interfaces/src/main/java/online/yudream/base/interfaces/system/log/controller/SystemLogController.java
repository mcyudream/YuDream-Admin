package online.yudream.base.interfaces.system.log.controller;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.log.dto.SystemLogStats;
import online.yudream.base.application.system.log.service.SystemLogAppService;
import online.yudream.base.application.system.log.service.SystemLogSettingAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.system.log.assembler.SystemLogWebAssembler;
import online.yudream.base.interfaces.system.log.request.DockerLogSettingsUpdateRequest;
import online.yudream.base.interfaces.system.log.res.DockerLogSettingsRes;
import online.yudream.base.interfaces.system.log.res.SystemLogRes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogAppService appService;
    private final SystemLogSettingAppService settingAppService;

    @GetMapping
    @PermissionRegister(code = "system:runtime-log:view", name = "查看系统日志", module = "系统管理", desc = "查看系统运行日志")
    public Result<PageResult<SystemLogRes>> page(@RequestParam(value = "level", required = false) String level,
                                                 @RequestParam(value = "modules", required = false) String modules,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "50") int size) {
        return Result.ok(SystemLogWebAssembler.toPage(appService.page(level, SystemLogWebAssembler.splitModules(modules), keyword, page, size)));
    }

    @GetMapping("/modules")
    @PermissionRegister(code = "system:runtime-log:view", name = "查看系统日志", module = "系统管理", desc = "查看系统运行日志")
    public Result<List<String>> modules() {
        return Result.ok(appService.modules());
    }

    @GetMapping("/stats")
    @PermissionRegister(code = "system:runtime-log:view", name = "查看系统日志", module = "系统管理", desc = "查看系统运行日志")
    public Result<SystemLogStats> stats() {
        return Result.ok(appService.stats());
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    @PermissionRegister(code = "system:runtime-log:view", name = "查看系统日志", module = "系统管理", desc = "实时查看系统运行日志")
    public SseEmitter stream(@RequestParam(value = "level", required = false) String level,
                             @RequestParam(value = "modules", required = false) String modules,
                             @RequestParam(value = "keyword", required = false) String keyword) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("time", System.currentTimeMillis())));
        } catch (Exception exception) {
            emitter.completeWithError(exception);
            return emitter;
        }
        try {
            AutoCloseable subscription = appService.subscribe(level, SystemLogWebAssembler.splitModules(modules), keyword,
                    entry -> {
                        try {
                            emitter.send(SseEmitter.event().name("log").data(SystemLogWebAssembler.toRes(entry)));
                        } catch (Exception ignored) {
                            // 客户端断开后发送失败，由 onCompletion/onTimeout/onError 统一关闭订阅。
                        }
                    });
            emitter.onCompletion(() -> close(subscription));
            emitter.onTimeout(() -> close(subscription));
            emitter.onError(ignored -> close(subscription));
        } catch (RuntimeException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @GetMapping("/download")
    @PermissionRegister(code = "system:runtime-log:download", name = "下载系统日志", module = "系统管理", desc = "导出当前筛选的系统日志")
    public void download(@RequestParam(value = "level", required = false) String level,
                         @RequestParam(value = "modules", required = false) String modules,
                         @RequestParam(value = "keyword", required = false) String keyword,
                         HttpServletResponse response) throws IOException {
        String text = SystemLogWebAssembler.toLogText(appService.export(level, SystemLogWebAssembler.splitModules(modules), keyword));
        String filename = "system-logs-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(java.time.Instant.now()) + ".log";
        response.setContentType("text/plain;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getWriter().write(text);
        response.getWriter().flush();
    }

    @DeleteMapping
    @PermissionRegister(code = "system:runtime-log:delete", name = "清空系统日志", module = "系统管理", desc = "清空内存中的系统日志缓存")
    public Result<Long> clear() {
        return Result.ok(appService.clear());
    }

    @GetMapping("/docker-settings")
    @PermissionRegister(code = "system:runtime-log:config", name = "配置容器日志", module = "系统管理", desc = "查看与配置 docker 容器日志采集")
    public Result<DockerLogSettingsRes> dockerSettings() {
        return Result.ok(SystemLogWebAssembler.toRes(settingAppService.dockerSettings()));
    }

    @PutMapping("/docker-settings")
    @PermissionRegister(code = "system:runtime-log:config", name = "配置容器日志", module = "系统管理", desc = "查看与配置 docker 容器日志采集")
    public Result<DockerLogSettingsRes> updateDockerSettings(@RequestBody DockerLogSettingsUpdateRequest request) {
        return Result.ok(SystemLogWebAssembler.toRes(settingAppService.update(SystemLogWebAssembler.toCmd(request))));
    }

    private void close(AutoCloseable subscription) {
        try {
            subscription.close();
        } catch (Exception ignored) {
        }
    }
}
