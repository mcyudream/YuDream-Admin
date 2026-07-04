package online.yudream.base.application.platform.form.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
public class DynamicFormSaveCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
    private String description;
    private String schemaJson;
    private String optionJson;
    private Boolean allowAnonymous;
    private DynamicFormStatus status;
}
