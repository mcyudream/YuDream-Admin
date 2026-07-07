package online.yudream.base.domain.system.user.service;

import online.yudream.base.domain.system.user.valobj.EmailVerifyTarget;

import java.util.Optional;

/**
 * 邮箱验证 Token 提供者。
 */
public interface EmailVerifyTokenProvider {

    String generate(Long userId, String email);

    Optional<EmailVerifyTarget> validate(String token);

    void remove(String token);
}
