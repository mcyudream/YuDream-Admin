package online.yudream.base.infra.platform.milky.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.milky.service.MilkyChatAppService;
import online.yudream.base.domain.platform.milky.event.MilkyEventPublished;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MilkyChatEventBridge {
    private final MilkyChatAppService chatAppService;

    @EventListener
    public void onEvent(MilkyEventPublished published) {
        chatAppService.publishEvent(published.connectionId(), published.event());
    }
}
