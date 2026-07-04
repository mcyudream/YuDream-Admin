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
public class FormFieldStatDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String field;
    private long filled;
    private long empty;
    private List<FormValueCountDTO> topValues;
}
