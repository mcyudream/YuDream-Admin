package online.yudream.base.infra.system.user.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.system.user.service.EmailVerifyTokenProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
    private static final Duration EXPIRE = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 生成邮箱验证 token。
     *
     * @param email 邮箱地址
     * @return 验证 token
     */
    public String generate(String email) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(KEY_PREFIX + token, email, EXPIRE);
        return token;
    }

    /**
     * 校验并获取 token 对应的邮箱。
     *
     * @param token 验证 token
     * @return 邮箱，若不存在或已过期则返回 empty
     */
    public Optional<String> validate(String token) {
        String email = stringRedisTemplate.opsForValue().get(KEY_PREFIX + token);
        return Optional.ofNullable(email);
    }

    /**
     * 删除 token。
     */
    public void remove(String token) {
        stringRedisTemplate.delete(KEY_PREFIX + token);
    }
}
