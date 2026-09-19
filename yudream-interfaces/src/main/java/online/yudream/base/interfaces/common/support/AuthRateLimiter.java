package online.yudream.base.interfaces.common.support;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证接口限流（进程内滑动窗口）：登录、注册、密码重置等匿名可触达端点
 * 按 IP/账号维度限制尝试频率，超出即拒绝，缓解凭据爆破与邮件轰炸。
 * <p>
 * 键与窗口由调用方给定；条目惰性清理。多副本部署时应将计数器外置（如 Redis），
 * 当前实现覆盖单实例部署场景。
 */
@Component
public class AuthRateLimiter {

    private static final int MAX_TRACKED_KEYS = 100_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** 校验并在超限时抛出业务异常；通过时记录本次命中。 */
    public void check(String bucket, String key, int limit, Duration window) {
        if (key == null || key.isBlank()) {
            return;
        }
        String composite = bucket + ":" + key;
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        Deque<Long> timestamps = hits.computeIfAbsent(composite, ignored -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                throw new BizException("尝试过于频繁，请稍后再试");
            }
            timestamps.addLast(now);
        }
        if (hits.size() > MAX_TRACKED_KEYS) {
            evictStale(windowStart);
        }
    }

    private void evictStale(long windowStart) {
        hits.entrySet().removeIf(entry -> {
            Deque<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }
}
