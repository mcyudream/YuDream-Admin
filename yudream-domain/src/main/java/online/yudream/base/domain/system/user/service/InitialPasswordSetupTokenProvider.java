package online.yudream.base.domain.system.user.service;

import online.yudream.base.domain.system.user.valobj.InitialPasswordSetupTarget;

import java.util.Optional;

/**
 * 初始密码设置 Token 提供者。
 * <p>
 * 与普通密码重置 {@link PasswordResetTokenProvider} 独立成族：
 * token 存储前缀不同，普通重置始终要求邮箱已验证，而初始密码设置
 * 面向服务端代置占位密码的外部导入新用户，完成设置时把邮箱标记为已验证。
 */
public interface InitialPasswordSetupTokenProvider {

    String generate(Long userId, String email);

    Optional<InitialPasswordSetupTarget> validate(String token);

    void remove(String token);
}
