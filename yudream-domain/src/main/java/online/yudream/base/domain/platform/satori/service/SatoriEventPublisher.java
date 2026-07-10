package online.yudream.base.domain.platform.satori.service;

import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent;

/** Publishes a normalized Satori event to in-process consumers. */
public interface SatoriEventPublisher {
    void publish(Long connectionId, SatoriEvent event);
}
