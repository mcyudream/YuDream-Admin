package online.yudream.base.infra.platform.satori.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import online.yudream.base.domain.platform.satori.enumerate.SatoriChannelType;
import online.yudream.base.domain.platform.satori.enumerate.SatoriLoginStatus;
import online.yudream.base.domain.platform.satori.enumerate.SatoriOpcode;
import online.yudream.base.domain.platform.satori.model.SatoriModels;

/** Satori v1 协议 JSON 编解码器，由基础设施层持有 Jackson 依赖与 wire 格式细节。 */
public final class SatoriJsonMapper {

    private SatoriJsonMapper() {
    }

    public static ObjectMapper createObjectMapper() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(SatoriModels.SatoriMeta.class, new SatoriMetaJsonDeserializer());
        module.addSerializer(SatoriModels.SatoriMeta.class, new SatoriMetaJsonSerializer());
        module.addDeserializer(SatoriChannelType.class, new SatoriIntegerEnumJsonDeserializer<>(SatoriChannelType.class, SatoriChannelType::fromValue));
        module.addSerializer(SatoriChannelType.class, new SatoriIntegerEnumJsonSerializer<>(SatoriChannelType.class, SatoriChannelType::value));
        module.addDeserializer(SatoriLoginStatus.class, new SatoriIntegerEnumJsonDeserializer<>(SatoriLoginStatus.class, SatoriLoginStatus::fromValue));
        module.addSerializer(SatoriLoginStatus.class, new SatoriIntegerEnumJsonSerializer<>(SatoriLoginStatus.class, SatoriLoginStatus::value));
        module.addDeserializer(SatoriOpcode.class, new SatoriIntegerEnumJsonDeserializer<>(SatoriOpcode.class, SatoriOpcode::fromValue));
        module.addSerializer(SatoriOpcode.class, new SatoriIntegerEnumJsonSerializer<>(SatoriOpcode.class, SatoriOpcode::value));
        return new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addMixIn(SatoriModels.SatoriEvent.class, SatoriEventJsonMixin.class)
                .registerModule(module);
    }
}
