package online.yudream.base.infra.platform.satori.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.function.IntFunction;

final class SatoriIntegerEnumJsonDeserializer<T> extends StdDeserializer<T> {

    private final IntFunction<T> factory;

    SatoriIntegerEnumJsonDeserializer(Class<T> type, IntFunction<T> factory) {
        super(type);
        this.factory = factory;
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (T) context.handleUnexpectedToken(handledType(), parser);
        }
        return factory.apply(parser.getValueAsInt());
    }
}
