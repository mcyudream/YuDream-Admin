package online.yudream.base.interfaces.platform.ai.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AguiStreamEventRes {
    private String type;
    private Long timestamp;
    private String threadId;
    private String runId;
    private String messageId;
    private String role;
    private String delta;
    private String toolCallId;
    private String toolCallName;
    private String parentMessageId;
    private Object content;
    private String activityType;
    private Object patch;
    private Object result;
    private String message;
}
