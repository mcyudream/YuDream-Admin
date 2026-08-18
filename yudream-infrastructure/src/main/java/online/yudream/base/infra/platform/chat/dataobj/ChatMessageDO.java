package online.yudream.base.infra.platform.chat.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.chat.enumerate.ChatMessageRole;
import online.yudream.base.domain.platform.chat.enumerate.ChatMessageStatus;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.domain.platform.chat.valobj.ChatAttachment;
import online.yudream.base.domain.platform.chat.valobj.ChatCitation;
import online.yudream.base.domain.platform.chat.valobj.ChatToolCall;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("platformChatMessage")
@CompoundIndex(name = "sessionCreated", def = "{'sessionId': 1, 'createTime': 1}")
public class ChatMessageDO extends BaseDO {
    @Indexed
    private Long sessionId;
    @Indexed
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
}
