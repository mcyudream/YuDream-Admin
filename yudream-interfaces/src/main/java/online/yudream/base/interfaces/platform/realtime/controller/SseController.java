package online.yudream.base.interfaces.platform.realtime.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.realtime.service.SseConnectionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 通用 SSE 端点（系统工具层，常开）。
 *
 * <p>原先受平台能力 sse 的启停控制，现为系统级基础设施；端点路径保持不变。
 */
@RestController
@RequestMapping("/api/platform/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseConnectionService sseConnectionService;

    @GetMapping("/connect")
    public SseEmitter connect() {
        // SSE 端点必须登录：浏览器 EventSource 无法携带 Authorization 头，
        // 前端已改为 fetch 流式读取并附带令牌。
        StpUtil.checkLogin();
        return sseConnectionService.connect();
    }
}
