package online.yudream.base.domain.system.mail.aggregate;

import lombok.Builder;
import lombok.Getter;
import online.yudream.base.domain.system.mail.valobj.MailAttachment;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class MailMessage {
    private final String from;
    private final List<String> to;
    private final List<String> cc;
    private final List<String> bcc;
    private final String subject;
    private final String text;          // 纯文本内容
    private final String html;          // HTML 内容（优先级高于 text）
    private final String templateId;    // Thymeleaf/FreeMarker 模板名
    private final Map<String, Object> templateVariables;
    private final List<MailAttachment> attachments;
}
