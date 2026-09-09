package online.yudream.base.domain.platform.mail.valobj;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入站邮件详情。htmlBody 供管理端沙箱预览，不回传给插件。
 */
public record InboundMailDetail(
        long uid,
        String subject,
        String from,
        String to,
        String cc,
        LocalDateTime receivedAt,
        boolean seen,
        String textBody,
        String htmlBody,
        List<InboundMailAttachment> attachments
) {
    public InboundMailDetail {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
