package online.yudream.base.interfaces.platform.form.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;

@Data
public class DynamicFormSaveRequest {
    @NotBlank(message = "表单名称不能为空")
    private String name;
    @NotBlank(message = "表单编码不能为空")
    private String code;
    private String description;
    @NotBlank(message = "表单设计内容不能为空")
    private String schemaJson;
    private String optionJson;
    private Boolean allowAnonymous;
    private DynamicFormStatus status;
}
