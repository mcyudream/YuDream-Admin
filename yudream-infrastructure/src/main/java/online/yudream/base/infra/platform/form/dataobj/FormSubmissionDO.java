package online.yudream.base.infra.platform.form.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformFormSubmission")
public class FormSubmissionDO extends BaseDO {
    @Indexed
    private Long formId;
    private String formCode;
    private Map<String, Object> data;
    private Long submitterId;
    private String submitterIp;
    private LocalDateTime submittedAt;
}
