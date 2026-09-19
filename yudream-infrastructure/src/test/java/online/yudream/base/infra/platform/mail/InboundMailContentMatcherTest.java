package online.yudream.base.infra.platform.mail;

import jakarta.activation.DataHandler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import online.yudream.base.plugin.spi.system.mail.PluginInboundMailQuery;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InboundMailContentMatcherTest {

    private static final String CODE = "APEVUKH9C8SSGS5D";
    private final InboundMailContentMatcher matcher = new InboundMailContentMatcher();

    @Test
    void matchesPlainTextBody() throws Exception {
        MimeMessage message = baseMessage();
        message.setText("请查收教育部学籍在线验证报告，在线验证码 " + CODE, "UTF-8");
        message.saveChanges();

        assertTrue(matcher.matches(message, query(CODE)));
    }

    @Test
    void matchesHtmlBodyEvenWhenCodeIsSplitByTags() throws Exception {
        MimeMessage message = baseMessage();
        message.setContent(
                "<p>教育部学籍在线验证报告</p><p>在线验证码 APE<span>VUKH</span>9C8SSGS5D</p>",
                "text/html; charset=UTF-8");
        message.saveChanges();

        assertTrue(matcher.matches(message, query(CODE)));
    }

    @Test
    void matchesPdfAttachmentWhenBodyHasNoCode() throws Exception {
        MimeMessage message = baseMessage();
        MimeBodyPart text = new MimeBodyPart();
        text.setText("请查收附件中的学籍在线验证报告", "UTF-8");
        MimeBodyPart pdf = pdfPart("chsi-report.pdf", "Xueji Online Report APEVUKH9C8SSGS5D");
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(text);
        multipart.addBodyPart(pdf);
        message.setContent(multipart);
        message.saveChanges();

        assertTrue(matcher.matches(message, query(CODE)));
        assertFalse(matcher.matches(message, query("ZZZZZZZZZZZZZZZZ")));
    }

    @Test
    void matchesSpacedCodeInsidePdfText() throws Exception {
        MimeMessage message = baseMessage();
        MimeBodyPart pdf = pdfPart("report.pdf", "APE VUKH 9C8S SGS5 D");
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(pdf);
        message.setContent(multipart);
        message.saveChanges();

        PluginInboundMailQuery noKeyword = new PluginInboundMailQuery(
                "default", CODE, List.of("chsi.com.cn"), List.of(), 0L, 0L);
        assertTrue(matcher.matches(message, noKeyword));
    }

    @Test
    void rejectsWhenAuthenticationResultsMissing() throws Exception {
        // 可信发件人判定 fail closed：接收方未提供 Authentication-Results 一律拒绝
        MimeMessage message = baseMessageWithoutAuthenticationResults();
        message.setText("请查收教育部学籍在线验证报告，在线验证码 " + CODE, "UTF-8");
        message.saveChanges();

        assertFalse(matcher.matches(message, query(CODE)));
    }

    @Test
    void rejectsSpoofedEnvelopeSenderMismatch() throws Exception {
        // From 声明可信域，但信封发件人（Return-Path）域不一致 → 拒绝
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("noreply@chsi.com.cn"));
        message.setSubject("学信网报告邮件");
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("inbox@example.com"));
        message.setHeader("Return-Path", "<bounce@evil.example>");
        message.setHeader("Authentication-Results", "mx.example.com; dmarc=pass header.from=chsi.com.cn");
        message.setText("验证码 " + CODE, "UTF-8");
        message.saveChanges();

        assertFalse(matcher.matches(message, query(CODE)));
    }

    @Test
    void matchesOctetStreamPdfAttachmentNamedAsPdf() throws Exception {
        MimeMessage message = baseMessage();
        MimeBodyPart text = new MimeBodyPart();
        text.setText("此邮件向您提供郭金龙的《教育部学籍在线验证报告》（附件中为PDF版）。", "UTF-8");
        MimeBodyPart pdf = new MimeBodyPart();
        pdf.setFileName("教育部学籍在线验证报告_郭金龙.pdf");
        pdf.setDataHandler(new DataHandler(new ByteArrayDataSource(
                pdfBytes("Online verification code " + CODE), "application/octet-stream")));
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(text);
        multipart.addBodyPart(pdf);
        message.setContent(multipart);
        message.saveChanges();

        assertTrue(matcher.matches(message, query(CODE)));
    }

    @Test
    void rejectsUntrustedSenderEvenWhenCodeMatches() throws Exception {
        MimeMessage message = baseMessage();
        message.setFrom(new InternetAddress("spoof@example.com"));
        message.setText("在线验证报告 " + CODE, "UTF-8");
        message.saveChanges();

        assertFalse(matcher.matches(message, query(CODE)));
    }

    @Test
    void rejectsMissingKeyword() throws Exception {
        MimeMessage message = baseMessage();
        message.setSubject("学信网报告邮件");
        message.setText("验证码 " + CODE, "UTF-8");
        message.saveChanges();

        assertFalse(matcher.matches(message, query(CODE)));
    }

    private static MimeMessage baseMessage() throws Exception {
        MimeMessage message = baseMessageWithoutAuthenticationResults();
        // 接收 MTA 写入的服务端认证结果与信封对齐
        message.setHeader("Return-Path", "<bounce@chsi.com.cn>");
        message.setHeader("Authentication-Results", "mx.example.com; dmarc=pass header.from=chsi.com.cn");
        return message;
    }

    private static MimeMessage baseMessageWithoutAuthenticationResults() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("noreply@chsi.com.cn"));
        message.setSubject("学信网报告邮件");
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("inbox@example.com"));
        return message;
    }

    private static PluginInboundMailQuery query(String code) {
        return new PluginInboundMailQuery(
                "default",
                code,
                List.of("chsi.com.cn"),
                List.of("在线验证报告"),
                0L,
                0L
        );
    }

    private static MimeBodyPart pdfPart(String filename, String text) throws Exception {
        MimeBodyPart pdf = new MimeBodyPart();
        pdf.setFileName(filename);
        pdf.setDataHandler(new DataHandler(new ByteArrayDataSource(pdfBytes(text), "application/pdf")));
        return pdf;
    }

    private static byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
