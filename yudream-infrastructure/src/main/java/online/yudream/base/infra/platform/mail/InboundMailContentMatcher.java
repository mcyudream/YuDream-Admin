package online.yudream.base.infra.platform.mail;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailQuery;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 只读抽取入站邮件的主题、正文、HTML 与 PDF 附件文本，再按发件域、验证码、关键词匹配。
 * 抽取结果不回传给插件。
 */
public final class InboundMailContentMatcher {

    static final int MAX_TEXT_CHARS = 64_000;
    static final int MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PARTS = 32;
    private static final int MAX_DEPTH = 8;

    public boolean matches(Message message, PluginInboundMailQuery query) throws Exception {
        return trustedSender(message, query) && containsCodeAndKeywords(message, query);
    }

    boolean trustedSender(Message message, PluginInboundMailQuery query) throws Exception {
        if (query == null || query.allowedFromDomains().isEmpty()) {
            return false;
        }
        for (String value : senderAddresses(message)) {
            int at = value.lastIndexOf('@');
            if (at < 0) {
                continue;
            }
            String domain = value.substring(at + 1).toLowerCase(Locale.ROOT);
            if (query.allowedFromDomains().stream().anyMatch(allowed ->
                    allowed != null && (domain.equals(allowed.toLowerCase(Locale.ROOT))
                            || domain.endsWith("." + allowed.toLowerCase(Locale.ROOT))))) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> senderAddresses(Message message) {
        Set<String> values = new LinkedHashSet<>();
        collectAddresses(values, headerAddresses(message, Message::getFrom));
        collectAddresses(values, headerAddresses(message, Message::getReplyTo));
        collectAddresses(values, headerValues(message, "Sender"));
        collectAddresses(values, headerValues(message, "Return-Path"));
        collectAddresses(values, headerValues(message, "X-Original-From"));
        collectAddresses(values, headerValues(message, "X-Envelope-From"));
        collectAddresses(values, headerValues(message, "From"));
        return values;
    }

    private static Address[] headerAddresses(Message message, HeaderAddressReader reader) {
        try {
            return reader.read(message);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String[] headerValues(Message message, String name) {
        try {
            return message.getHeader(name);
        } catch (Exception ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface HeaderAddressReader {
        Address[] read(Message message) throws Exception;
    }

    private static void collectAddresses(Set<String> values, Address[] addresses) {
        if (addresses == null) {
            return;
        }
        for (Address address : addresses) {
            if (address instanceof InternetAddress internet) {
                addMailbox(values, internet.getAddress());
            } else if (address != null) {
                addMailbox(values, address.toString());
            }
        }
    }

    private static void collectAddresses(Set<String> values, String[] headers) {
        if (headers == null) {
            return;
        }
        for (String header : headers) {
            if (header == null || header.isBlank()) {
                continue;
            }
            try {
                for (InternetAddress address : InternetAddress.parseHeader(header, false)) {
                    addMailbox(values, address.getAddress());
                }
            } catch (Exception ignored) {
                addMailbox(values, header);
            }
        }
    }

    private static void addMailbox(Set<String> values, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String value = raw.trim();
        if (value.startsWith("<") && value.endsWith(">") && value.length() > 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        int at = value.lastIndexOf('@');
        if (at > 0) {
            values.add(value);
        }
    }

    boolean containsCodeAndKeywords(Message message, PluginInboundMailQuery query) throws Exception {
        if (query == null) {
            return false;
        }
        String subject = message.getSubject() == null ? "" : message.getSubject();
        String extracted = extractText(message);
        String haystack = compactWhitespace(subject + "\n" + extracted);
        if (haystack.length() > MAX_TEXT_CHARS) {
            haystack = haystack.substring(0, MAX_TEXT_CHARS);
        }
        String compact = haystack.replace(" ", "");
        String code = compactCode(query.verificationCode());
        if (code.isEmpty() || !compact.toUpperCase(Locale.ROOT).contains(code.toUpperCase(Locale.ROOT))) {
            return false;
        }
        final String searchable = haystack;
        return query.requiredKeywords().stream().allMatch(keyword ->
                keyword == null || keyword.isBlank() || searchable.contains(keyword.trim()));
    }

    String extractText(Part part) throws Exception {
        StringBuilder out = new StringBuilder();
        collect(part, out, 0, 0);
        return out.toString();
    }

    private int collect(Part part, StringBuilder out, int depth, int parts) throws Exception {
        if (part == null || depth > MAX_DEPTH || parts >= MAX_PARTS || out.length() >= MAX_TEXT_CHARS) {
            return parts;
        }
        parts++;
        Object content = contentOf(part);
        if (content instanceof Multipart multipart) {
            int count = Math.min(multipart.getCount(), MAX_PARTS);
            for (int i = 0; i < count; i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                parts = collect(bodyPart, out, depth + 1, parts);
            }
            return parts;
        }
        if (content instanceof Part nested) {
            return collect(nested, out, depth + 1, parts);
        }
        String filename = decodedFileName(part);
        if (isPdf(part, filename, content)) {
            append(out, extractPdf(bytesOf(part, content)));
            return parts;
        }
        if (content instanceof String text) {
            append(out, looksLikeHtml(part, text) ? htmlToText(text) : text);
            return parts;
        }
        byte[] raw = content instanceof InputStream input ? readLimited(input) : bytesOfOrEmpty(part, content);
        if (raw.length > 0) {
            append(out, looksLikePdf(raw) ? extractPdf(raw) : new String(raw, StandardCharsets.UTF_8));
        }
        return parts;
    }

    private static Object contentOf(Part part) {
        try {
            return part.getContent();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean looksLikeHtml(Part part, String text) {
        return mimeStartsWith(part, "text/html") || (text != null && text.contains("<") && text.contains(">"));
    }

    private static boolean isPdf(Part part, String filename, Object content) {
        if (mimeStartsWith(part, "application/pdf") || filenameEndsWith(filename, ".pdf")) {
            return true;
        }
        if (content instanceof byte[] bytes) {
            return looksLikePdf(bytes);
        }
        return false;
    }

    private static boolean looksLikePdf(byte[] bytes) {
        if (bytes == null || bytes.length < 5) {
            return false;
        }
        return bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private static String decodedFileName(Part part) {
        try {
            String filename = part.getFileName();
            if (filename == null || filename.isBlank()) {
                return "";
            }
            String decoded = filename;
            for (int i = 0; i < 3; i++) {
                String next = MimeUtility.decodeText(decoded);
                if (next.equals(decoded)) {
                    break;
                }
                decoded = next;
            }
            return decoded;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean mimeStartsWith(Part part, String expected) {
        try {
            if (part.isMimeType(expected)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            String type = part.getContentType();
            if (type == null) {
                return false;
            }
            return type.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean filenameEndsWith(String filename, String suffix) {
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(suffix);
    }

    private static void append(StringBuilder out, String value) {
        if (value == null || value.isBlank() || out.length() >= MAX_TEXT_CHARS) {
            return;
        }
        if (!out.isEmpty()) {
            out.append('\n');
        }
        int remaining = MAX_TEXT_CHARS - out.length();
        out.append(value, 0, Math.min(value.length(), remaining));
    }

    public static String stringContent(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof InputStream input) {
            return new String(readLimited(input), StandardCharsets.UTF_8);
        }
        return "";
    }

    private static byte[] bytesOf(Part part, Object content) throws Exception {
        if (content instanceof byte[] bytes) {
            int length = Math.min(bytes.length, MAX_ATTACHMENT_BYTES);
            byte[] copy = new byte[length];
            System.arraycopy(bytes, 0, copy, 0, length);
            return copy;
        }
        if (content instanceof InputStream input) {
            return readLimited(input);
        }
        try (InputStream input = part.getInputStream()) {
            return readLimited(input);
        }
    }

    private static byte[] bytesOfOrEmpty(Part part, Object content) {
        try {
            return bytesOf(part, content);
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static byte[] readLimited(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (output.size() + read > MAX_ATTACHMENT_BYTES) {
                break;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String extractPdf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String withoutScripts = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?s)<[^>]+>", " ");
        return withoutScripts
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
    }

    static String compactWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    static String compactCode(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "");
    }

    public static long sizeOf(Part part) throws Exception {
        int size = part.getSize();
        return Math.max(size, 0);
    }

    public static String contentType(Part part) throws Exception {
        String type = part.getContentType();
        if (type == null || type.isBlank()) {
            return "application/octet-stream";
        }
        int separator = type.indexOf(';');
        return separator < 0 ? type.trim() : type.substring(0, separator).trim();
    }

    public static boolean isAttachment(Part part) throws Exception {
        String disposition = part.getDisposition();
        return Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || (part.getFileName() != null && !part.getFileName().isBlank() && !part.isMimeType("text/*"));
    }
}
