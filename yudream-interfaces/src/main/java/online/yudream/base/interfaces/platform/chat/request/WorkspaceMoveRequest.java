package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkspaceMoveRequest {
    @NotBlank(message = "原路径不能为空")
    private String from;
    @NotBlank(message = "目标路径不能为空")
    private String to;
}
