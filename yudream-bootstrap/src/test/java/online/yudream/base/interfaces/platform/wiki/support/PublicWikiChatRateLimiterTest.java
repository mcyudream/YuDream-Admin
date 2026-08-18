package online.yudream.base.interfaces.platform.wiki.support;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicWikiChatRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-15T00:00:00Z"));

    @Test
    void rateLimitsRequestsPerFixedWindow() {
        PublicWikiChatRateLimiter limiter = limiter(2, 10, Duration.ofMinutes(1));

        limiter.acquire("1.2.3.4", "docs").close();
        limiter.acquire("1.2.3.4", "docs").close();

        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", "docs"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过于频繁");
    }

    @Test
    void resetsCountAfterWindowExpires() {
        PublicWikiChatRateLimiter limiter = limiter(1, 10, Duration.ofMinutes(1));

        limiter.acquire("1.2.3.4", "docs").close();
        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", "docs")).isInstanceOf(BizException.class);

        clock.advance(Duration.ofMinutes(1).plusMillis(1));

        PublicWikiChatRateLimiter.Permit permit = limiter.acquire("1.2.3.4", "docs");
        permit.close();
    }

    @Test
    void enforcesConcurrentLimit() {
        PublicWikiChatRateLimiter limiter = limiter(100, 1, Duration.ofMinutes(1));

        PublicWikiChatRateLimiter.Permit first = limiter.acquire("1.2.3.4", "docs");
        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", "docs"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("同时提问人数过多");

        first.close();

        PublicWikiChatRateLimiter.Permit second = limiter.acquire("1.2.3.4", "docs");
        second.close();
    }

    @Test
    void releaseRestoresConcurrencyAndAllowsLazyCleanup() {
        PublicWikiChatRateLimiter limiter = limiter(10, 2, Duration.ofMinutes(1));

        PublicWikiChatRateLimiter.Permit permit = limiter.acquire("1.2.3.4", "docs");
        assertThat(limiter.trackedKeys()).isEqualTo(1);

        permit.close();
        clock.advance(Duration.ofMinutes(1).plusMillis(1));
        limiter.sweepExpired(clock.millis());

        assertThat(limiter.trackedKeys()).isZero();
    }

    @Test
    void closingPermitTwiceDoesNotReleaseAnExtraSlot() {
        PublicWikiChatRateLimiter limiter = limiter(100, 1, Duration.ofMinutes(1));

        PublicWikiChatRateLimiter.Permit first = limiter.acquire("1.2.3.4", "docs");
        first.close();
        first.close();

        PublicWikiChatRateLimiter.Permit second = limiter.acquire("1.2.3.4", "docs");
        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", "docs"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("同时提问人数过多");
        second.close();
    }

    @Test
    void changingSpaceSlugDoesNotBypassIpRateLimit() {
        PublicWikiChatRateLimiter limiter = limiter(1, 10, Duration.ofMinutes(1));

        limiter.acquire("1.2.3.4", "docs").close();

        assertThatThrownBy(() -> limiter.acquire("1.2.3.4", "other"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("过于频繁");
        limiter.acquire("1.2.3.5", "docs").close();
    }

    private PublicWikiChatRateLimiter limiter(int requestsPerWindow, int maxConcurrent, Duration window) {
        return new PublicWikiChatRateLimiter(clock, requestsPerWindow, window, maxConcurrent);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
