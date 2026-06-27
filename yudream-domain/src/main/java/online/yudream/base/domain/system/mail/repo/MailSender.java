package online.yudream.base.domain.system.mail.repo;

import online.yudream.base.domain.system.mail.aggregate.MailMessage;

public interface MailSender {
    void send(MailMessage message);
}
