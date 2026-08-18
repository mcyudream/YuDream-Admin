package online.yudream.base.domain.platform.chat.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.enumerate.ChatMessageRole;
import online.yudream.base.domain.platform.chat.enumerate.ChatMessageStatus;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatAttachment;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import online.yudream.base.domain.platform.chat.valobj.ChatToolCall;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ChatMessage extends BaseDomain {

    private Long sessionId;
    private Long userId;
    private ChatMessageRole role;
    private String content;
    private String reasoning;
    private List<ChatCitation> citations = new ArrayList<>();
    private List<ChatToolCall> tools = new ArrayList<>();
    private List<ChatActivity> activities = new ArrayList<>();
    private List<ChatAttachment> attachments = new ArrayList<>();
    private AiUsage usage = AiUsage.empty();
    private ChatMessageStatus status = ChatMessageStatus.COMPLETED;
    private String errorMessage;

    public static ChatMessage user(Long sessionId, Long userId, String content, List<ChatAttachment> attachments) {
        return ChatMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(ChatMessageRole.USER)
                .content(content == null ? "" : content)
                .attachments(attachments == null ? new ArrayList<>() : new ArrayList<>(attachments))
                .status(ChatMessageStatus.COMPLETED)
                .build();
    }

    public static ChatMessage assistant(Long sessionId, Long userId) {
        return ChatMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(ChatMessageRole.ASSISTANT)
                .content("")
                .status(ChatMessageStatus.STREAMING)
                .build();
    }

    /**
     * 完成助手消息并保存正文、推理过程和用量。
     */
    public void complete(String content, String reasoning, AiUsage usage) {
        this.content = content == null ? "" : content;
        this.reasoning = reasoning == null ? "" : reasoning;
        this.usage = usage == null ? AiUsage.empty() : usage;
        this.status = ChatMessageStatus.COMPLETED;
    }

    public void complete(String content, AiUsage usage) {
        complete(content, reasoning, usage);
    }

    public void fail(String message) {
        this.status = ChatMessageStatus.FAILED;
        this.errorMessage = message == null ? "问答失败，请稍后重试" : message;
    }

    public void cancel() {
        if (this.status == ChatMessageStatus.STREAMING || this.status == ChatMessageStatus.PENDING) {
            this.status = ChatMessageStatus.CANCELLED;
        }
    }

    public void belongsTo(Long userId) {
        if (userId == null || !userId.equals(this.userId)) {
            throw new BizException("无权访问该消息");
        }
    }
}
