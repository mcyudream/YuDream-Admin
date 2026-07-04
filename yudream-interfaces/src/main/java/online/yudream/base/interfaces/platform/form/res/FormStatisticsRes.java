package online.yudream.base.interfaces.platform.form.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FormStatisticsRes {
    private Long formId;
    private String formCode;
    private long total;
    private long today;
    private long last7Days;
    private List<FormFieldStatRes> fields;
}
