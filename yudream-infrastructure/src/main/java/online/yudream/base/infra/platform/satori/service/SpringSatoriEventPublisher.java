package online.yudream.base.infra.platform.satori.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent;
import online.yudream.base.domain.platform.satori.service.SatoriEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** Spring adapter; later plugin dispatchers can subscribe without coupling to transport details. */
@Service
@RequiredArgsConstructor
public class SpringSatoriEventPublisher implements SatoriEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Long connectionId, SatoriEvent event) {
        applicationEventPublisher.publishEvent(new SatoriPublishedEvent(connectionId, event));
    }

    public record SatoriPublishedEvent(Long connectionId, SatoriEvent event) { }
}
