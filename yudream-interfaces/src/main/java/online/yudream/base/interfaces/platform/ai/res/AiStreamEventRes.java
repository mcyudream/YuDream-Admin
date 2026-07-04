package online.yudream.base.interfaces.platform.ai.res;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AiStreamEventRes {
    private String event;
    private String action;
    private String module;
    private String traceId;
    private Long timestamp;
    private Map<String, Object> payload;
}
