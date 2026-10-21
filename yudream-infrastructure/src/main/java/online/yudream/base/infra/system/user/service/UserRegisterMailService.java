package online.yudream.base.infra.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.mail.aggregate.MailMessage;
import online.yudream.base.domain.system.mail.repo.MailSender;
import online.yudream.base.domain.system.user.service.UserRegisterMailSender;
import online.yudream.base.infra.system.integration.SystemIntegrationSettings;
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
    private final SystemIntegrationSettings integrationSettings;

    /**
     * 发送邮箱验证邮件。
     *
     * @param username 用户名
     * @param email    邮箱地址
     * @param token    验证 token
     */
    public void sendVerifyEmail(String username, String email, String token) {
        String verifyUrl = integrationSettings.effectiveWebUrl() + "/verify-email?token=" + token;
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

    @Override
    public void sendPasswordResetEmail(String username, String email, String token) {
        String resetUrl = integrationSettings.effectiveWebUrl() + "/reset-password?token=" + token;
        MailMessage message = MailMessage.builder()
                .to(List.of(email))
                .subject("重置密码")
                .templateId("password-reset")
                .templateVariables(Map.of(
                        "username", username,
                        "resetUrl", resetUrl
                ))
                .build();
        mailSender.send(message);
    }

    /**
     * 发送初始密码设置邮件（外部导入/模板创建的新用户）。
     * <p>
     * 复用 /reset-password 前端页面；token 族与普通重置独立，应用层据此区分处理。
     * 邮件经由异步发送器提交，此处不代表实际送达。
     */
    @Override
    public void sendInitialPasswordSetupEmail(String username, String email, String token) {
        String setupUrl = integrationSettings.effectiveWebUrl() + "/reset-password?token=" + token;
        MailMessage message = MailMessage.builder()
                .to(List.of(email))
                .subject("设置初始密码")
                .templateId("initial-password-setup")
                .templateVariables(Map.of(
                        "username", username,
                        "setupUrl", setupUrl
                ))
                .build();
        mailSender.send(message);
    }
}
