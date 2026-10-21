package online.yudream.base.domain.system.user.service;

/**
 * 用户注册邮件发送器。
 */
public interface UserRegisterMailSender {

    void sendVerifyEmail(String username, String email, String token);

    void sendPasswordResetEmail(String username, String email, String token);

    /**
     * 发送初始密码设置邮件（外部导入/模板创建的新用户一次性设置登录密码）。
     */
    void sendInitialPasswordSetupEmail(String username, String email, String token);
}
