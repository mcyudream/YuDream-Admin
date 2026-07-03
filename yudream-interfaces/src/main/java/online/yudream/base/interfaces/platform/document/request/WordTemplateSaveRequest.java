package online.yudream.base.interfaces.platform.document.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;

import java.util.HashMap;
import java.util.Map;

@Data
public class WordTemplateSaveRequest {
    @NotBlank(message = "模板名称不能为空")
    private String name;
    @NotBlank(message = "模板编码不能为空")
    private String code;
    private Map<String, String> placeholders = new HashMap<>();
    private String description;
    private TemplateStatus status;
}
