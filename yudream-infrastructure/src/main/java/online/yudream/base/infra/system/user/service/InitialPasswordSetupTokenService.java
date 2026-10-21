package online.yudream.base.infra.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.service.InitialPasswordSetupTokenProvider;
import online.yudream.base.domain.system.user.valobj.InitialPasswordSetupTarget;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 基于 Redis 的初始密码设置 Token 服务。
 * <p>
 * 面向外部导入/模板创建的新用户一次性设置初始密码：
 * - token 由 {@link SecureRandom} 生成（256 位熵，URL 安全 Base64），不使用自增来源；
 * - 与普通密码重置 {@code password:reset:} 使用不同 key 前缀，互不冲突；
 * - 目标用户邮箱可以尚未验证，设置成功后由应用层把邮箱标记为已验证。
 */
@Service
@RequiredArgsConstructor
public class InitialPasswordSetupTokenService implements InitialPasswordSetupTokenProvider {

    private static final String KEY_PREFIX = "password:initial-setup:";
    private static final String PAYLOAD_SEPARATOR = "|";
    /** 管理端/导入流程发出，允许更长的处理窗口，有效期比普通重置更久。 */
    private static final Duration EXPIRE = Duration.ofHours(24);
    private static final int RANDOM_BYTES = 32;

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate(Long userId, String email) {
        if (userId == null || !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("初始密码设置目标不能为空");
        }
        String token = newToken();
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, userId + PAYLOAD_SEPARATOR + email, EXPIRE);
        return token;
    }

    @Override
    public Optional<InitialPasswordSetupTarget> validate(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        String payload = stringRedisTemplate.opsForValue().get(KEY_PREFIX + token);
        return parsePayload(payload);
    }

    @Override
    public void remove(String token) {
        if (StringUtils.hasText(token)) {
            stringRedisTemplate.delete(KEY_PREFIX + token);
        }
    }

    private String newToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Optional<InitialPasswordSetupTarget> parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }
        int separatorIndex = payload.indexOf(PAYLOAD_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == payload.length() - 1) {
            return Optional.empty();
        }
        try {
            Long userId = Long.parseLong(payload.substring(0, separatorIndex));
            String email = payload.substring(separatorIndex + 1);
            return StringUtils.hasText(email)
                    ? Optional.of(new InitialPasswordSetupTarget(userId, email))
                    : Optional.empty();
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
