package online.yudream.base.domain.platform.satori.event;

import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent;

/** Published after a Satori event has been durably accepted by the application service. */
public record SatoriEventPublished(Long connectionId, SatoriEvent event) {
}
