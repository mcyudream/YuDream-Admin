package online.yudream.base.infra.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.service.PasswordResetTokenProvider;
import online.yudream.base.domain.system.user.valobj.PasswordResetTarget;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 Redis 的密码重置 Token 服务。
 */
@Service
@RequiredArgsConstructor
public class PasswordResetTokenService implements PasswordResetTokenProvider {

    private static final String KEY_PREFIX = "password:reset:";
    private static final String PAYLOAD_SEPARATOR = "|";
    private static final Duration EXPIRE = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String generate(Long userId, String email) {
        if (userId == null || !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("密码重置目标不能为空");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, userId + PAYLOAD_SEPARATOR + email, EXPIRE);
        return token;
    }

    @Override
    public Optional<PasswordResetTarget> validate(String token) {
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

    private Optional<PasswordResetTarget> parsePayload(String payload) {
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
            return StringUtils.hasText(email) ? Optional.of(new PasswordResetTarget(userId, email)) : Optional.empty();
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
