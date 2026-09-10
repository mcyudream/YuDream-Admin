package online.yudream.base.interfaces.platform.cms.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HomePagePresetSaveRequest {
    @NotBlank(message = "方案名称不能为空")
    private String name;
    private String description;
}
