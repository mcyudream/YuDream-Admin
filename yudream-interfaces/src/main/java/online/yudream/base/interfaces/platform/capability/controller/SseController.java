package online.yudream.base.interfaces.platform.capability.controller;

import lombok.RequiredArgsConstructor;
import online.yudream.base.interfaces.platform.capability.service.SseCapabilityProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/platform/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseCapabilityProvider sseCapabilityProvider;

    @GetMapping("/connect")
    public SseEmitter connect() {
        return sseCapabilityProvider.connect();
    }
}
