package online.yudream.base.infra.platform.satori.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import online.yudream.base.domain.platform.satori.model.SatoriModels;

import java.io.IOException;
import java.util.List;
import java.util.Map;

final class SatoriMetaJsonDeserializer extends StdDeserializer<SatoriModels.SatoriMeta> {

    SatoriMetaJsonDeserializer() {
        super(SatoriModels.SatoriMeta.class);
    }

    @Override
    public SatoriModels.SatoriMeta deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (!node.isObject()) {
            return SatoriModels.SatoriMeta.of(null, null, null, List.of(), Map.of());
        }
        Map<String, Object> fields = parser.getCodec().treeToValue(node, Map.class);
        Object rawFeatures = fields.get("features");
        List<String> features = rawFeatures instanceof List<?> values
                ? values.stream().map(String::valueOf).toList()
                : List.of();
        return SatoriModels.SatoriMeta.of(
                text(fields.get("impl")),
                text(fields.get("protocol_version")),
                text(fields.get("adapter")),
                features,
                fields
        );
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
