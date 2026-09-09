package online.yudream.base.domain.platform.mail.valobj;

import java.time.LocalDateTime;

/**
 * 入站邮件列表摘要。uid 是 IMAP UID，不是 Snowflake。
 */
public record InboundMailSummary(
        long uid,
        String subject,
        String from,
        String to,
        LocalDateTime receivedAt,
        boolean seen,
        boolean hasAttachment
) {
}
