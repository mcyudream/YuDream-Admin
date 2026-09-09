package online.yudream.base.infra.platform.mail.service;

import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import online.yudream.base.domain.platform.mail.valobj.InboundMailAttachment;
import online.yudream.base.domain.platform.mail.valobj.InboundMailDetail;
import online.yudream.base.domain.platform.mail.valobj.InboundMailSummary;
import online.yudream.base.infra.platform.mail.InboundMailContentMatcher;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * IMAP MIME 解析：列表摘要与详情正文。抽取结果仅供管理端，不回传给插件。
 */
public final class InboundMailMimeParser {

    static final int MAX_BODY_CHARS = 200_000;
    private static final int MAX_PARTS = 32;
    private static final int MAX_DEPTH = 8;

    private InboundMailMimeParser() {
    }

    public static InboundMailSummary toSummary(Message message, long uid) throws Exception {
        return new InboundMailSummary(
                uid,
                blankToDefault(decodeHeader(message.getSubject()), "（无主题）"),
                formatFrom(message),
                formatAddresses(message.getRecipients(Message.RecipientType.TO)),
                toLocalDateTime(message.getReceivedDate()),
                message.isSet(Flags.Flag.SEEN),
                hasAttachment(message)
        );
    }

    public static InboundMailDetail toDetail(Message message, long uid) throws Exception {
        ParsedBody parsed = parseBody(message);
        return new InboundMailDetail(
                uid,
                blankToDefault(decodeHeader(message.getSubject()), "（无主题）"),
                formatFrom(message),
                formatAddresses(message.getRecipients(Message.RecipientType.TO)),
                formatAddresses(message.getRecipients(Message.RecipientType.CC)),
                toLocalDateTime(message.getReceivedDate()),
                message.isSet(Flags.Flag.SEEN),
                parsed.textBody,
                parsed.htmlBody,
                parsed.attachments
        );
    }

    static ParsedBody parseBody(Part part) throws Exception {
        ParsedBody parsed = new ParsedBody();
        collect(part, parsed, 0, 0);
        if (!StringUtils.hasText(parsed.textBody) && StringUtils.hasText(parsed.htmlBody)) {
            parsed.textBody = InboundMailContentMatcher.htmlToText(parsed.htmlBody);
        }
        parsed.textBody = truncate(parsed.textBody, MAX_BODY_CHARS);
        parsed.htmlBody = truncate(parsed.htmlBody, MAX_BODY_CHARS);
        return parsed;
    }

    private static int collect(Part part, ParsedBody parsed, int depth, int parts) throws Exception {
        if (part == null || depth > MAX_DEPTH || parts >= MAX_PARTS) {
            return parts;
        }
        parts++;
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            int count = Math.min(multipart.getCount(), MAX_PARTS);
            for (int i = 0; i < count; i++) {
                parts = collect(multipart.getBodyPart(i), parsed, depth + 1, parts);
            }
            return parts;
        }
        if (InboundMailContentMatcher.isAttachment(part)) {
            parsed.attachments.add(new InboundMailAttachment(
                    blankToDefault(decodeHeader(part.getFileName()), "未命名附件"),
                    InboundMailContentMatcher.contentType(part),
                    InboundMailContentMatcher.sizeOf(part),
                    Part.INLINE.equalsIgnoreCase(part.getDisposition())
            ));
            return parts;
        }
        if (part.isMimeType("text/html") && !StringUtils.hasText(parsed.htmlBody)) {
            parsed.htmlBody = InboundMailContentMatcher.stringContent(part);
            return parts;
        }
        if (part.isMimeType("text/plain") && !StringUtils.hasText(parsed.textBody)) {
            parsed.textBody = InboundMailContentMatcher.stringContent(part);
        }
        return parts;
    }

    public static boolean hasAttachment(Part part) throws Exception {
        return hasAttachment(part, 0, 0);
    }

    private static boolean hasAttachment(Part part, int depth, int parts) throws Exception {
        if (part == null || depth > MAX_DEPTH || parts >= MAX_PARTS) {
            return false;
        }
        parts++;
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            int count = Math.min(multipart.getCount(), MAX_PARTS);
            for (int i = 0; i < count; i++) {
                if (hasAttachment(multipart.getBodyPart(i), depth + 1, parts + i)) {
                    return true;
                }
            }
            return false;
        }
        return InboundMailContentMatcher.isAttachment(part);
    }

    static String formatFrom(Message message) {
        try {
            String from = formatAddresses(message.getFrom());
            if (StringUtils.hasText(from)) {
                return from;
            }
        } catch (Exception ignored) {
        }
        return String.join(", ", InboundMailContentMatcher.senderAddresses(message));
    }

    static String formatAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        List<String> values = new ArrayList<>(addresses.length);
        for (Address address : addresses) {
            if (address instanceof InternetAddress internet) {
                String personal = decodeHeader(internet.getPersonal());
                String mailbox = internet.getAddress();
                if (StringUtils.hasText(personal) && StringUtils.hasText(mailbox)) {
                    values.add(personal + " <" + mailbox + ">");
                } else if (StringUtils.hasText(mailbox)) {
                    values.add(mailbox);
                } else if (StringUtils.hasText(personal)) {
                    values.add(personal);
                }
            } else if (address != null) {
                values.add(address.toString());
            }
        }
        return String.join(", ", values);
    }

    static String decodeHeader(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        try {
            return MimeUtility.decodeText(raw).trim();
        } catch (Exception ignored) {
            return raw.trim();
        }
    }

    static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    static String truncate(String value, int max) {
        if (!StringUtils.hasText(value) || value.length() <= max) {
            return value == null ? "" : value;
        }
        return value.substring(0, max);
    }

    static String blankToDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    static final class ParsedBody {
        String textBody = "";
        String htmlBody = "";
        final List<InboundMailAttachment> attachments = new ArrayList<>();
    }
}
