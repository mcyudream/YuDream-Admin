package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.mail.aggregate.MailMessage;
import online.yudream.base.domain.system.mail.repo.MailSender;
import online.yudream.base.plugin.spi.system.mail.PluginMailMessage;
import online.yudream.base.plugin.spi.system.mail.PluginMailService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PluginMailFrameworkService implements PluginMailService {

    private final MailSender mailSender;

    @Override
    public void send(PluginMailMessage message) {
        if (message == null || message.to().isEmpty()) {
            throw new IllegalArgumentException("邮件收件人不能为空");
        }
        if (!StringUtils.hasText(message.subject())) {
            throw new IllegalArgumentException("邮件主题不能为空");
        }
        mailSender.send(MailMessage.builder()
                .from(message.from())
                .to(message.to())
                .cc(message.cc())
                .bcc(message.bcc())
                .subject(message.subject())
                .text(message.text())
                .html(message.html())
                .build());
    }
}
