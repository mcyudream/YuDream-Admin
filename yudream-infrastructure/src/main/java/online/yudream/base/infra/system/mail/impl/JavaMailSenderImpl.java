package online.yudream.base.infra.system.mail.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.mail.aggregate.MailMessage;
import online.yudream.base.domain.system.mail.repo.MailSender;
import online.yudream.base.domain.system.mail.repo.MailTemplateEngine;
import online.yudream.base.domain.system.mail.valobj.MailAttachment;
import online.yudream.base.infra.config.prop.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class JavaMailSenderImpl implements MailSender {

    private final JavaMailSender javaMailSender;
    private final MailTemplateEngine templateEngine;
    private final MailProperties mailProperties;

    @Override
    public void send(MailMessage message) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // 发件人
            String from = message.getFrom() != null ? message.getFrom() : mailProperties.getFrom();
            helper.setFrom(from);

            // 收件人
            helper.setTo(message.getTo().toArray(new String[0]));

            // 抄送
            if (message.getCc() != null && !message.getCc().isEmpty()) {
                helper.setCc(message.getCc().toArray(new String[0]));
            }

            // 密送
            if (message.getBcc() != null && !message.getBcc().isEmpty()) {
                helper.setBcc(message.getBcc().toArray(new String[0]));
            }

            // 主题
            helper.setSubject(message.getSubject());

            // 正文：优先模板 > html > text
            String html = message.getHtml();
            if (html == null && message.getTemplateId() != null) {
                html = templateEngine.render(message.getTemplateId(), message.getTemplateVariables());
            }

            if (html != null) {
                helper.setText(html, true);
            } else if (message.getText() != null) {
                helper.setText(message.getText(), false);
            }

            // 附件
            if (message.getAttachments() != null) {
                for (MailAttachment att : message.getAttachments()) {
                    helper.addAttachment(
                            att.getFilename(),
                            () -> new ByteArrayInputStream(att.getContent()),
                            att.getContentType()
                    );
                }
            }

            javaMailSender.send(mimeMessage);
            log.info("邮件发送成功: to={}, subject={}", message.getTo(), message.getSubject());

        } catch (MessagingException e) {
            log.error("邮件发送失败: to={}, subject={}", message.getTo(), message.getSubject(), e);
            throw new MailSendException("邮件发送失败: " + e.getMessage(), e);
        }
    }
}