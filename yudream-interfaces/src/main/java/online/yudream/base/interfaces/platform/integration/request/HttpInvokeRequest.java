package online.yudream.base.interfaces.platform.integration.request;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class HttpInvokeRequest {
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private String body;
}
