package online.yudream.base.infra.platform.satori.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import online.yudream.base.domain.platform.satori.model.SatoriModels;

import java.io.IOException;

final class SatoriMetaJsonSerializer extends StdSerializer<SatoriModels.SatoriMeta> {

    SatoriMetaJsonSerializer() {
        super(SatoriModels.SatoriMeta.class);
    }

    @Override
    public void serialize(SatoriModels.SatoriMeta value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeStartObject();
        writeString(generator, "impl", value.impl());
        writeString(generator, "protocol_version", value.protocolVersion());
        writeString(generator, "adapter", value.adapter());
        generator.writeObjectField("features", value.features());
        for (var field : value.extraFields().entrySet()) {
            generator.writeObjectField(field.getKey(), field.getValue());
        }
        generator.writeEndObject();
    }

    private void writeString(JsonGenerator generator, String name, String value) throws IOException {
        if (value != null) {
            generator.writeStringField(name, value);
        }
    }
}
