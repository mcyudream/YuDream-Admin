package online.yudream.base.infra.system.mail.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.system.mail.aggregate.MailMessage;
import online.yudream.base.domain.system.mail.repo.MailSender;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 异步邮件发送装饰器 —— 包装真实发送器，避免阻塞业务线程
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class AsyncMailSender implements MailSender {

    private final MailSender delegate;      // 注入 JavaMailSenderImpl
    private final Executor mailExecutor;

    @Override
    public void send(MailMessage message) {
        mailExecutor.execute(() -> {
            try {
                delegate.send(message);
            } catch (Exception e) {
                log.error("异步邮件发送失败: to={}, subject={}", message.getTo(), message.getSubject(), e);
            }
        });
    }
}