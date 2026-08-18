package online.yudream.base.interfaces.platform.chat.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatTurnRequest {
    @NotBlank(message = "历史消息角色不能为空")
    private String role;
    @NotBlank(message = "历史消息内容不能为空")
    private String content;
}
