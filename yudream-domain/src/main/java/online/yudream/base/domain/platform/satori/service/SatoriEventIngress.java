package online.yudream.base.domain.platform.satori.service;

import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriLogin;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta;

import java.util.List;

/** Shared application-facing ingress for WebSocket and WebHook transports. */
public interface SatoriEventIngress {
    void acceptEvent(Long connectionId, SatoriEvent event, String rawData);
    void synchronizeLogins(Long connectionId, List<SatoriLogin> logins);
    void acceptMeta(Long connectionId, SatoriMeta meta);
}
