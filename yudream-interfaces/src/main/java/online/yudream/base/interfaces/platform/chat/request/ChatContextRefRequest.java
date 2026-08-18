package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatContextRefRequest {
    @NotBlank(message = "上下文类型不能为空")
    @Size(max = 40, message = "上下文类型不能超过 40 字符")
    private String type;
    @NotBlank(message = "上下文目标不能为空")
    @Size(max = 200, message = "上下文目标不能超过 200 字符")
    private String target;
    @Size(max = 200, message = "上下文名称不能超过 200 字符")
    private String label;
}
