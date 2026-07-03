package online.yudream.base.application.platform.integration.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class HttpInvokeCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long connectorId;
    private Map<String, String> headers = new HashMap<>();
    private Map<String, String> queryParams = new HashMap<>();
    private String body;
}
