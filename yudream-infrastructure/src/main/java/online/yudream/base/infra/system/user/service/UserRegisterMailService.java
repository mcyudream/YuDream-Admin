package online.yudream.base.infra.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.mail.aggregate.MailMessage;
import online.yudream.base.domain.system.mail.repo.MailSender;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 用户注册邮件服务实现。
 */
@Service
@RequiredArgsConstructor
public class UserRegisterMailService implements UserRegisterMailSender {

    private final MailSender mailSender;

    @Value("${app.web-url:${app.base-url:http://localhost:9000}}")
    private String webUrl;

    /**
     * 发送邮箱验证邮件。
     *
     * @param username 用户名
     * @param email    邮箱地址
     * @param token    验证 token
     */
    public void sendVerifyEmail(String username, String email, String token) {
        String verifyUrl = webUrl + "/verify-email?token=" + token;
        MailMessage message = MailMessage.builder()
                .to(List.of(email))
                .subject("邮箱验证")
                .templateId("welcome")
                .templateVariables(Map.of(
                        "username", username,
                        "verifyUrl", verifyUrl
                ))
                .build();
        mailSender.send(message);
    }
}
