package online.yudream.base.domain.system.user.service;

import online.yudream.base.domain.system.user.valobj.PasswordResetTarget;

import java.util.Optional;

/**
 * 密码重置 Token 提供者。
 */
public interface PasswordResetTokenProvider {

    String generate(Long userId, String email);

    Optional<PasswordResetTarget> validate(String token);

    void remove(String token);
}
