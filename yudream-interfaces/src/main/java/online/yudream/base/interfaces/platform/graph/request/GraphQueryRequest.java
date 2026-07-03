package online.yudream.base.interfaces.platform.graph.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class GraphQueryRequest {
    @NotBlank(message = "Cypher 不能为空")
    private String cypher;
    private Map<String, Object> params;
}
