package online.yudream.base.domain.platform.satori.service;

import online.yudream.base.domain.platform.satori.model.SatoriApiModels.InternalRequest;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.SatoriApiContext;

/** Explicit escape hatch for adapter-native APIs; callers must be separately authorized. */
public interface SatoriInternalGateway {
    Object invoke(SatoriApiContext context, InternalRequest request);
}
