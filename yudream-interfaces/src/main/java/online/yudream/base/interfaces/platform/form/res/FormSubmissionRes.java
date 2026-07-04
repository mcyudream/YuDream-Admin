package online.yudream.base.interfaces.platform.form.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class FormSubmissionRes {
    private Long id;
    private Long formId;
    private String formCode;
    private Map<String, Object> data;
    private Long submitterId;
    private String submitterIp;
    private LocalDateTime submittedAt;
    private LocalDateTime createTime;
}
