package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspacePathRequest {
    @NotBlank(message = "路径不能为空")
    private String path;
}
