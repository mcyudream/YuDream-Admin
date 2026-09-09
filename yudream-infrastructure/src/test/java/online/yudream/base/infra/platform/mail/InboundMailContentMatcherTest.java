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
    void matchesWhenFromHeaderMissingButSenderDomainTrusted() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setSubject("教育部学籍在线验证报告（郭金龙）");
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("yudream@yudream.online"));
        message.setHeader("Sender", "noreply@mail.chsi.com.cn");
        message.setHeader("Return-Path", "<bounce@chsi.com.cn>");
        message.setText("请查收教育部学籍在线验证报告，在线验证码 " + CODE, "UTF-8");
        message.saveChanges();

        assertTrue(matcher.matches(message, query(CODE)));
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
