package online.yudream.base.interfaces.platform.milky.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.service.MilkyChatAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/platform/milky/connections/{connectionId}/chat")
@RequiredArgsConstructor
public class MilkyChatController {
    private final MilkyChatAppService appService;

    @GetMapping("/conversations")
    @PermissionRegister(code = "platform:milky:view", name = "查看 Milky 会话", module = "Milky", desc = "查看好友与群聊")
    public Result<Object> conversations(@PathVariable Long connectionId) {
        return Result.ok(appService.conversations(connectionId));
    }

    @GetMapping("/history")
    @PermissionRegister(code = "platform:milky:view", name = "查看 Milky 历史消息", module = "Milky", desc = "查看消息历史")
    public Result<Object> history(@PathVariable Long connectionId, @RequestParam String scene, @RequestParam String peerId,
                                  @RequestParam(required = false) String start, @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(appService.history(connectionId, scene, peerId, start, limit));
    }

    @GetMapping(value = "/events", produces = "text/event-stream")
    @PermissionRegister(code = "platform:milky:view", name = "订阅 Milky 消息", module = "Milky", desc = "订阅实时消息")
    public SseEmitter events(@PathVariable Long connectionId) {
        SseEmitter emitter = new SseEmitter(0L);
        try { emitter.send(SseEmitter.event().name("connected").data(Map.of("connectionId", String.valueOf(connectionId)))); }
        catch (Exception exception) { emitter.completeWithError(exception); return emitter; }
        try {
            AutoCloseable subscription = appService.subscribe(connectionId, event -> {
                try {
                    Map<String, Object> payload = new java.util.LinkedHashMap<>();
                    payload.put("time", event.time());
                    payload.put("selfId", event.selfId());
                    payload.put("eventType", event.eventType());
                    payload.put("data", event.data());
                    emitter.send(SseEmitter.event().name(event.eventType()).data(payload));
                } catch (Exception exception) { emitter.completeWithError(exception); }
            });
            emitter.onCompletion(() -> close(subscription));
            emitter.onTimeout(() -> close(subscription));
            emitter.onError(ignored -> close(subscription));
        } catch (RuntimeException exception) { emitter.completeWithError(exception); }
        return emitter;
    }

    private void close(AutoCloseable subscription) {
        try { subscription.close(); } catch (Exception ignored) { }
    }

    @PostMapping("/messages")
    @PermissionRegister(code = "platform:milky:send", name = "发送 Milky 消息", module = "Milky", desc = "发送 QQ 消息")
    public Result<Object> send(@PathVariable Long connectionId, @RequestBody Map<String, Object> body) {
        return Result.ok(appService.send(connectionId, String.valueOf(body.get("scene")), String.valueOf(body.get("peerId")), body.get("message")));
    }

    @PostMapping("/api/{api}")
    @PermissionRegister(code = "platform:milky:internal", name = "调用 Milky 原生接口", module = "Milky", desc = "调用全部 Milky API")
    public Result<Object> invoke(@PathVariable Long connectionId, @PathVariable String api, @RequestBody(required = false) Map<String, Object> payload) {
        return Result.ok(appService.invoke(connectionId, api, payload));
    }
}
