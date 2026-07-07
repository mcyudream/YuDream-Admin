package online.yudream.base.domain.system.user.service;

/**
 * 用户注册邮件发送器。
 */
public interface UserRegisterMailSender {

    void sendVerifyEmail(String username, String email, String token);

    void sendPasswordResetEmail(String username, String email, String token);
}
