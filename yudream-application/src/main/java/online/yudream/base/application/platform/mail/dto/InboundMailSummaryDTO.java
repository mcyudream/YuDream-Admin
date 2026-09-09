package online.yudream.base.application.platform.mail.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundMailSummaryDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long uid;
    private String subject;
    private String from;
    private String to;
    private LocalDateTime receivedAt;
    private Boolean seen;
    private Boolean hasAttachment;
}
