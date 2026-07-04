package online.yudream.base.application.platform.form.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormStatisticsDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long formId;
    private String formCode;
    private long total;
    private long today;
    private long last7Days;
    private List<FormFieldStatDTO> fields;
}
