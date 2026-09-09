package online.yudream.base.domain.platform.mail.valobj;

/**
 * 入站邮件附件元数据；v1 不返回二进制内容。
 */
public record InboundMailAttachment(
        String filename,
        String contentType,
        long size,
        boolean inline
) {
}
