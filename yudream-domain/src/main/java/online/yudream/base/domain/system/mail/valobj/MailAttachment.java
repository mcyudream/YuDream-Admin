package online.yudream.base.domain.system.mail.valobj;

import lombok.Builder;
import lombok.Getter;

/**
 * 邮件附件值对象
 */
@Getter
@Builder
public class MailAttachment {
    private final String filename;
    private final String contentType;
    private final byte[] content;
}
