package online.yudream.base.interfaces.platform.wiki.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WikiChatRequest {
    @NotBlank(message = "问题不能为空")
    @Size(max = 4000, message = "问题长度不能超过 4000 字符")
    private String question;

    @Valid
    @Size(max = 10, message = "历史对话不能超过 10 轮")
    private List<ChatTurn> history;

    /**
     * 可选会话标识：提供时走 Harness 有状态会话（服务端管理跨轮次上下文，history 可不传）。
     */
    @Size(max = 64, message = "会话标识长度不能超过 64 字符")
    private String sessionId;

    @Data
    public static class ChatTurn {
        @Size(max = 20, message = "角色长度不能超过 20 字符")
        private String role;

        @Size(max = 8000, message = "内容长度不能超过 8000 字符")
        private String content;
    }
}
