package online.yudream.base.interfaces.platform.cms.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/** 禁止Jackson把JSON数字强制转为字符串，避免上游已丢精度的revision进入CAS。 */
public class StudioRevisionDeserializer extends StdDeserializer<String> {
    public StudioRevisionDeserializer() { super(String.class); }
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
        return parser.getText();
    }
}
