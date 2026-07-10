package online.yudream.base.domain.platform.satori.service;

import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta;

public interface SatoriEventCodec {
    SatoriEvent decodeEvent(String rawData);
    SatoriMeta decodeMeta(String rawData);
}
