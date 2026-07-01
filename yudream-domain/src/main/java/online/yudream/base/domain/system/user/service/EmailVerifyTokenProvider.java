package online.yudream.base.domain.system.user.service;

import java.util.Optional;

/**
 * 邮箱验证 Token 提供者。
 */
public interface EmailVerifyTokenProvider {

    String generate(String email);

    Optional<String> validate(String token);

    void remove(String token);
}
