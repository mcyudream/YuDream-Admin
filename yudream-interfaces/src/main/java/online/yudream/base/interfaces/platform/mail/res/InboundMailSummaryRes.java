package online.yudream.base.interfaces.platform.mail.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InboundMailSummaryRes {
    private Long uid;
    private String subject;
    private String from;
    private String to;
    private LocalDateTime receivedAt;
    private Boolean seen;
    private Boolean hasAttachment;
}
