package online.yudream.base.interfaces.platform.document.request;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class WordGenerateRequest {
    private Map<String, Object> data = new HashMap<>();
}
