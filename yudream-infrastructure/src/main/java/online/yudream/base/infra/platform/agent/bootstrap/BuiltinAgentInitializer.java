package online.yudream.base.infra.platform.agent.bootstrap;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.agent.service.BuiltinAgentInitializerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.agent", name = "enabled", havingValue = "true")
public class BuiltinAgentInitializer {
    private final BuiltinAgentInitializerService initializer;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        initializer.initialize();
    }
}
