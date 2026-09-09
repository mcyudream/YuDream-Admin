package online.yudream.base.infra.platform.mail.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import online.yudream.base.domain.platform.mail.valobj.InboundMailDetail;
import online.yudream.base.infra.platform.mail.InboundMailContentMatcher;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class InboundMailMimeParserTest {

    @Test
    void prefersPlainTextAndKeepsHtmlForPreview() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom("Alice <alice@example.com>");
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, "verify@example.com");
        message.setSubject("=?UTF-8?B?6aqM6K+B56CB?=", "UTF-8");
        MimeMultipart multipart = new MimeMultipart("alternative");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("验证码 123456", "UTF-8");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>验证码 <b>123456</b></p>", "text/html; charset=UTF-8");
        multipart.addBodyPart(text);
        multipart.addBodyPart(html);
        message.setContent(multipart);
        message.saveChanges();

        InboundMailDetail detail = InboundMailMimeParser.toDetail(message, 42L);

        assertThat(detail.uid()).isEqualTo(42L);
        assertThat(detail.subject()).isEqualTo("验证码");
        assertThat(detail.from()).contains("alice@example.com");
        assertThat(detail.textBody()).contains("123456");
        assertThat(detail.htmlBody()).contains("<b>123456</b>");
        assertThat(detail.attachments()).isEmpty();
    }

    @Test
    void recordsAttachmentMetadataWithoutBinary() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom("notify@example.com");
        message.setSubject("附件");
        MimeMultipart mixed = new MimeMultipart("mixed");
        MimeBodyPart body = new MimeBodyPart();
        body.setText("请查收附件");
        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setFileName("receipt.pdf");
        attachment.setContent("pdf-bytes", "application/pdf");
        attachment.setDisposition(jakarta.mail.Part.ATTACHMENT);
        mixed.addBodyPart(body);
        mixed.addBodyPart(attachment);
        message.setContent(mixed);
        message.saveChanges();

        InboundMailDetail detail = InboundMailMimeParser.toDetail(message, 7L);

        assertThat(InboundMailMimeParser.hasAttachment(message)).isTrue();
        assertThat(detail.attachments()).singleElement().satisfies(item -> {
            assertThat(item.filename()).isEqualTo("receipt.pdf");
            assertThat(item.contentType()).isEqualTo("application/pdf");
            assertThat(item.inline()).isFalse();
        });
        assertThat(detail.textBody()).contains("请查收附件");
    }

    @Test
    void fallsBackToSenderHeaderWhenFromMissing() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setRecipients(jakarta.mail.Message.RecipientType.TO, "yudream@yudream.online");
        message.setSubject("教育部学籍在线验证报告（郭金龙）");
        message.setHeader("Sender", "noreply@mail.chsi.com.cn");
        message.setHeader("Return-Path", "<bounce@chsi.com.cn>");
        message.setText("附件中为PDF版", "UTF-8");
        message.saveChanges();

        InboundMailDetail detail = InboundMailMimeParser.toDetail(message, 9L);
        assertThat(detail.from()).contains("noreply@mail.chsi.com.cn");
    }

    @Test
    void fallsBackToHtmlTextWhenPlainPartMissing() {
        String text = InboundMailContentMatcher.htmlToText("<script>alert(1)</script><p>Hello&nbsp;world</p>");
        assertThat(text).contains("Hello").contains("world");
    }
}
