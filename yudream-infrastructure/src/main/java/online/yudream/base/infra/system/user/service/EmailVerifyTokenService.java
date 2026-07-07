package online.yudream.base.infra.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.service.EmailVerifyTokenProvider;
import online.yudream.base.domain.system.user.valobj.EmailVerifyTarget;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 Redis 的邮箱验证 Token 服务。
 */
@Service
@RequiredArgsConstructor
public class EmailVerifyTokenService implements EmailVerifyTokenProvider {

    private static final String KEY_PREFIX = "email:verify:";
    private static final String PAYLOAD_SEPARATOR = "|";
    private static final Duration EXPIRE = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成邮箱验证 token。
     *
     * @param userId 用户 ID
     * @param email 邮箱地址
     * @return 验证 token
     */
    public String generate(Long userId, String email) {
        if (userId == null || !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("邮箱验证目标不能为空");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, userId + PAYLOAD_SEPARATOR + email, EXPIRE);
        return token;
    }

    /**
     * 校验并获取 token 对应的用户与邮箱。
     *
     * @param token 验证 token
     * @return 验证目标，若不存在或已过期则返回 empty
     */
    public Optional<EmailVerifyTarget> validate(String token) {
        String payload = stringRedisTemplate.opsForValue().get(KEY_PREFIX + token);
        return parsePayload(payload);
    }

    /**
     * 删除 token。
     */
    public void remove(String token) {
        stringRedisTemplate.delete(KEY_PREFIX + token);
    }

    private Optional<EmailVerifyTarget> parsePayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Optional.empty();
        }
        int separatorIndex = payload.indexOf(PAYLOAD_SEPARATOR);
        if (separatorIndex < 0) {
            return Optional.of(new EmailVerifyTarget(null, payload));
        }
        if (separatorIndex == 0 || separatorIndex == payload.length() - 1) {
            return Optional.empty();
        }
        try {
            Long userId = Long.parseLong(payload.substring(0, separatorIndex));
            String email = payload.substring(separatorIndex + 1);
            return StringUtils.hasText(email) ? Optional.of(new EmailVerifyTarget(userId, email)) : Optional.empty();
        }
        catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
