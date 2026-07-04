package online.yudream.base.application.platform.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AiToolCallDTO {
    private String toolName;
    private String action;
    private String permissionCode;
    private String message;
    private Map<String, Object> payload;
}
