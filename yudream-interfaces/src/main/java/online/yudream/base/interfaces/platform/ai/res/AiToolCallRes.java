package online.yudream.base.interfaces.platform.ai.res;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AiToolCallRes {
    private String toolName;
    private String action;
    private String permissionCode;
    private String message;
    private Map<String, Object> payload;
}
