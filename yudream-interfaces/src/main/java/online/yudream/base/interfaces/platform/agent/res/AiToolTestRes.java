package online.yudream.base.interfaces.platform.agent.res;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/** 管理端工具测试结果。 */
@Data
@Builder
public class AiToolTestRes {
    private Boolean success;
    private String message;
    private String action;
    private String toolCode;
    private String source;
    private Long durationMillis;
    private Map<String, Object> payload;
}
