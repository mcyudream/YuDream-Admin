package online.yudream.base.infra.platform.satori.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriEvent;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta;
import online.yudream.base.domain.platform.satori.service.SatoriEventCodec;
import online.yudream.base.infra.platform.satori.json.SatoriJsonMapper;
import org.springframework.stereotype.Service;

@Service
public class SatoriEventJsonCodec implements SatoriEventCodec {
    private final ObjectMapper mapper = SatoriJsonMapper.createObjectMapper();

    @Override
    public SatoriEvent decodeEvent(String rawData) {
        return decode(rawData, SatoriEvent.class, "Satori EVENT 数据无效");
    }

    @Override
    public SatoriMeta decodeMeta(String rawData) {
        return decode(rawData, SatoriMeta.class, "Satori META 数据无效");
    }

    private <T> T decode(String rawData, Class<T> type, String message) {
        try {
            return mapper.readValue(rawData, type);
        } catch (JsonProcessingException exception) {
            throw new BizException(message);
        }
    }
}
