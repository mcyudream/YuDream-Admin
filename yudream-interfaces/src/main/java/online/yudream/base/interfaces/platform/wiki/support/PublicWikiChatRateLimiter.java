package online.yudream.base.interfaces.platform.wiki.support;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 公开 Wiki 问答限流器：按客户端 IP 实施固定窗口频率，并按「客户端 IP + spaceSlug」实施并发上限。
 * 窗口与并发计数均为进程内状态，多实例部署时应改为集中式限流。
 */
@Component
public class PublicWikiChatRateLimiter {

    public interface Permit extends AutoCloseable {
        @Override
        void close();
    }

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final Clock clock;
    private final int maxRequestsPerWindow;
    private final long windowMillis;
    private final int maxConcurrent;
    private final ConcurrentHashMap<String, KeyState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WindowState> ipWindows = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis = new AtomicLong();

    @Autowired
    public PublicWikiChatRateLimiter(
            @Value("${yudream.platform.wiki.chat.public-rate-limit.requests-per-minute:10}") int maxRequestsPerMinute,
            @Value("${yudream.platform.wiki.chat.public-rate-limit.max-concurrent:2}") int maxConcurrent) {
        this(Clock.systemUTC(), maxRequestsPerMinute, Duration.ofMinutes(1), maxConcurrent);
    }

    PublicWikiChatRateLimiter(Clock clock, int maxRequestsPerWindow, Duration window, int maxConcurrent) {
        if (clock == null) throw new IllegalArgumentException("clock 不能为空");
        if (maxRequestsPerWindow < 1) throw new IllegalArgumentException("maxRequestsPerWindow 必须大于 0");
        if (window == null || window.toMillis() <= 0) throw new IllegalArgumentException("window 必须为正时长");
        if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent 必须大于 0");
        this.clock = clock;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMillis = window.toMillis();
        this.maxConcurrent = maxConcurrent;
    }

    public Permit acquire(String clientIp, String spaceSlug) {
        String key = key(clientIp, spaceSlug);
        long now = clock.millis();
        maybeCleanupExpired(now);
        WindowState ipWindow;
        KeyState state;
        synchronized (states) {
            ipWindow = ipWindows.computeIfAbsent(clientIp, ignored -> new WindowState(now));
            synchronized (ipWindow) {
                resetWindowIfExpired(ipWindow, now);
                if (ipWindow.windowCount >= maxRequestsPerWindow) {
                    throw new BizException("提问过于频繁，请稍后再试");
                }
                ipWindow.windowCount++;
            }

            state = states.computeIfAbsent(key, ignored -> new KeyState(now, maxConcurrent));
            synchronized (state) {
                if (!state.semaphore.tryAcquire()) {
                    synchronized (ipWindow) {
                        ipWindow.windowCount--;
                    }
                    throw new BizException("同时提问人数过多，请稍后再试");
                }
                state.activeLeases.incrementAndGet();
                ipWindow.activeLeases.incrementAndGet();
            }
        }

        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (!released.compareAndSet(false, true)) return;
            synchronized (states) {
                synchronized (state) {
                    state.semaphore.release();
                    int remaining = state.activeLeases.decrementAndGet();
                    if (remaining == 0 && clock.millis() - state.windowStartMillis >= windowMillis) {
                        states.remove(key, state);
                    }
                }
                WindowState current = ipWindows.get(clientIp);
                if (current != null && current.activeLeases.decrementAndGet() == 0
                        && clock.millis() - current.windowStartMillis >= windowMillis) {
                    ipWindows.remove(clientIp, current);
                }
            }
        };
    }

    private String key(String clientIp, String spaceSlug) {
        if (clientIp == null || clientIp.isBlank()) throw new BizException("无法识别客户端地址");
        if (spaceSlug == null || spaceSlug.isBlank() || spaceSlug.length() > 120) {
            throw new BizException("知识库标识无效");
        }
        return clientIp + '|' + spaceSlug;
    }

    private void resetWindowIfExpired(WindowState state, long now) {
        if (now - state.windowStartMillis >= windowMillis) {
            state.windowStartMillis = now;
            state.windowCount = 0;
        }
    }

    private void maybeCleanupExpired(long now) {
        long last = lastCleanupMillis.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS || !lastCleanupMillis.compareAndSet(last, now)) return;
        sweepExpired(now);
    }

    void sweepExpired(long now) {
        synchronized (states) {
            states.forEach((key, state) -> {
                synchronized (state) {
                    if (state.activeLeases.get() == 0 && now - state.windowStartMillis >= windowMillis) {
                        states.remove(key, state);
                    }
                }
            });
            ipWindows.forEach((ip, state) -> {
                synchronized (state) {
                    if (state.activeLeases.get() == 0 && now - state.windowStartMillis >= windowMillis) {
                        ipWindows.remove(ip, state);
                    }
                }
            });
        }
    }

    int trackedKeys() {
        return states.size();
    }

    private static final class KeyState {
        private final Semaphore semaphore;
        private final AtomicInteger activeLeases = new AtomicInteger();
        private final long windowStartMillis;

        KeyState(long windowStartMillis, int maxConcurrent) {
            this.semaphore = new Semaphore(maxConcurrent);
            this.windowStartMillis = windowStartMillis;
        }
    }

    private static final class WindowState {
        private long windowStartMillis;
        private int windowCount;
        private final AtomicInteger activeLeases = new AtomicInteger();

        WindowState(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}
