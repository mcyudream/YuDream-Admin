package online.yudream.base.interfaces.platform.agent.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class AiToolTestRequest {
    @NotBlank
    private String code;
    /** 调用参数，按工具输入 JSON Schema。 */
    private Map<String, Object> arguments;
}
