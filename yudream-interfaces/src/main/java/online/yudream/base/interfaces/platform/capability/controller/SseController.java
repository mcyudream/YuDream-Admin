package online.yudream.base.interfaces.platform.capability.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.capability.service.SseCapabilityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/platform/sse")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.sse", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SseController {

    private final SseCapabilityProvider sseCapabilityProvider;

    @GetMapping("/connect")
    public SseEmitter connect() {
        // SSE 端点必须登录：浏览器 EventSource 无法携带 Authorization 头，
        // 前端已改为 fetch 流式读取并附带令牌。
        StpUtil.checkLogin();
        return sseCapabilityProvider.connect();
    }
}
