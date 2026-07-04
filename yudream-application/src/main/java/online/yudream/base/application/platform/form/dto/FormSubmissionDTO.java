package online.yudream.base.application.platform.form.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSubmissionDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long formId;
    private String formCode;
    private Map<String, Object> data;
    private Long submitterId;
    private String submitterIp;
    private LocalDateTime submittedAt;
    private LocalDateTime createTime;
}
