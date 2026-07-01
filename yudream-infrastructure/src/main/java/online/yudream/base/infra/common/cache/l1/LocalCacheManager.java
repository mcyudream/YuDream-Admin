package online.yudream.base.infra.common.cache.l1;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.common.cache.nulls.NullValue;
import online.yudream.base.infra.common.cache.prop.CacheProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地一级缓存管理器。
 * <p>
 * 通过 {@code yudream.cache.l1.enabled} 控制开关，关闭时所有操作均为 no-op。
 */
@Slf4j
@Component
public class LocalCacheManager {

    private final boolean enabled;
    private final Cache<String, Object> cache;

    public LocalCacheManager(CacheProperties cacheProperties) {
        this.enabled = cacheProperties.getL1().isEnabled();
        if (enabled) {
            CacheProperties.L1Properties l1 = cacheProperties.getL1();
            this.cache = Caffeine.newBuilder()
                    .maximumSize(l1.getMaximumSize())
                    .expireAfterWrite(l1.getExpireAfterWrite(), TimeUnit.SECONDS)
                    .recordStats()
                    .build();
            log.info("Local cache(L1) enabled, maximumSize={}, expireAfterWrite={}s", l1.getMaximumSize(), l1.getExpireAfterWrite());
        } else {
            this.cache = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Object get(String key) {
        if (!enabled) {
            return null;
        }
        Object value = cache.getIfPresent(key);
        if (value != null) {
            log.debug("L1 cache hit, key={}", key);
        }
        return value;
    }

    public void put(String key, Object value) {
        if (!enabled) {
            return;
        }
        cache.put(key, value);
        log.debug("L1 cache put, key={}", key);
    }

    public void invalidate(String key) {
        if (!enabled || key == null) {
            return;
        }
        cache.invalidate(key);
        log.debug("L1 cache invalidate, key={}", key);
    }

    public void invalidateAll() {
        if (!enabled) {
            return;
        }
        cache.invalidateAll();
        log.info("L1 cache invalidate all");
    }

    public boolean isNullValue(Object value) {
        return value instanceof NullValue;
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats stats() {
        if (!enabled) {
            return null;
        }
        return cache.stats();
    }
}
