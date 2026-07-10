package online.yudream.base.infra.platform.satori.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.function.ToIntFunction;

final class SatoriIntegerEnumJsonSerializer<T> extends StdSerializer<T> {

    private final ToIntFunction<T> valueExtractor;

    SatoriIntegerEnumJsonSerializer(Class<T> type, ToIntFunction<T> valueExtractor) {
        super(type);
        this.valueExtractor = valueExtractor;
    }

    @Override
    public void serialize(T value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeNumber(valueExtractor.applyAsInt(value));
    }
}
