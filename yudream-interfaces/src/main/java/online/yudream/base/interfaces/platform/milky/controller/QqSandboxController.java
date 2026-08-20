package online.yudream.base.interfaces.platform.milky.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.sandbox.service.QqSandboxAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.milky.assembler.QqSandboxWebAssembler;
import online.yudream.base.interfaces.platform.milky.request.QqSandboxCreateRequest;
import online.yudream.base.interfaces.platform.milky.request.QqSandboxMessageRequest;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxMessageRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxPresetRes;
import online.yudream.base.interfaces.platform.milky.res.QqSandboxSessionRes;
import online.yudream.base.interfaces.platform.milky.support.QqSandboxStreamSupport;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/platform/plugin-devtools/qq-sandbox")
@RequiredArgsConstructor
public class QqSandboxController {
    private final QqSandboxAppService appService;
    private final QqSandboxStreamSupport streamSupport;

    @GetMapping("/presets")
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "查看 QQ 沙箱预设", module = "插件开发者工具", desc = "查看 QQ 沙箱预设")
    public Result<List<QqSandboxPresetRes>> presets() {
        return Result.ok(QqSandboxWebAssembler.presets());
    }

    @PostMapping("/sessions")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "创建 QQ 沙箱会话", module = "插件开发者工具", desc = "创建插件 QQ 消息沙箱")
    public Result<QqSandboxSessionRes> create(@Valid @RequestBody QqSandboxCreateRequest request) {
        return Result.ok(QqSandboxWebAssembler.toRes(appService.create(QqSandboxWebAssembler.toCmd(request))));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "执行 QQ 沙箱消息", module = "插件开发者工具", desc = "向插件发送合成 Milky 消息")
    public Result<QqSandboxMessageRes> send(@PathVariable String sessionId,
                                            @Valid @RequestBody QqSandboxMessageRequest request) {
        return Result.ok(QqSandboxWebAssembler.toMessageRes(
                appService.send(sessionId, QqSandboxWebAssembler.toCmd(request)), request));
    }

    @GetMapping(value = "/sessions/{sessionId}/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PermissionRegister(code = "platform:plugin-devtools:view", name = "订阅 QQ 沙箱事件", module = "插件开发者工具", desc = "订阅 QQ 沙箱执行时间线")
    public SseEmitter events(@PathVariable String sessionId) {
        return streamSupport.subscribe(sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PermissionRegister(code = "platform:plugin-devtools:manage", name = "删除 QQ 沙箱会话", module = "插件开发者工具", desc = "删除 QQ 沙箱会话")
    public Result<Void> delete(@PathVariable String sessionId) {
        appService.delete(sessionId);
        return Result.ok();
    }
}
