package online.yudream.base.interfaces.platform.mail.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class InboundMailDetailRes {
    private Long uid;
    private String subject;
    private String from;
    private String to;
    private String cc;
    private LocalDateTime receivedAt;
    private Boolean seen;
    private String textBody;
    private String htmlBody;
    private List<InboundMailAttachmentRes> attachments;
}
