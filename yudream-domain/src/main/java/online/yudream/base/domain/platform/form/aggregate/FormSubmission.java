package online.yudream.base.domain.platform.form.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FormSubmission extends BaseDomain {

    private Long formId;
    private String formCode;
    private Map<String, Object> data;
    private Long submitterId;
    private String submitterIp;
    private LocalDateTime submittedAt;

    public static FormSubmission create(DynamicForm form, Map<String, Object> data, Long submitterId, String submitterIp) {
        if (form == null || form.getId() == null) {
            throw new BizException("表单不存在");
        }
        if (!form.published()) {
            throw new BizException("表单未发布");
        }
        FormSubmission submission = new FormSubmission();
        submission.formId = form.getId();
        submission.formCode = form.getCode();
        submission.data = new HashMap<>(data == null ? Map.of() : data);
        submission.submitterId = submitterId;
        submission.submitterIp = submitterIp;
        submission.submittedAt = LocalDateTime.now();
        return submission;
    }
}
