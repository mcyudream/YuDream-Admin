package online.yudream.base.infra.common.cache.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.cache.anno.Cache;
import online.yudream.base.domain.common.cache.anno.DeleteCache;
import online.yudream.base.domain.common.cache.anno.RefreshCache;
import online.yudream.base.infra.common.cache.bloom.RedisBloomFilter;
import online.yudream.base.infra.common.cache.l1.LocalCacheManager;
import online.yudream.base.infra.common.cache.metrics.CacheMetricsService;
import online.yudream.base.infra.common.cache.nulls.NullValue;
import online.yudream.base.infra.common.cache.prop.CacheProperties;
import online.yudream.base.infra.common.cache.spel.CacheKeySpelParser;
import online.yudream.base.infra.common.redis.RedisService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * 缓存注解统一切面。
 * <p>
 * 支持二级缓存（Caffeine L1 + Redis L2）、缓存空值、布隆过滤器防穿透、异步刷新、统计监控。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CacheAspect {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisService redisService;
    private final CacheKeySpelParser keyParser;
    private final CacheProperties cacheProperties;
    private final LocalCacheManager localCacheManager;
    private final RedisBloomFilter bloomFilter;
    private final CacheMetricsService metricsService;
    private final Executor cacheRefreshExecutor;

    @Around("@annotation(cache)")
    public Object aroundCache(ProceedingJoinPoint point, Cache cache) throws Throwable {
        if (!cacheProperties.isEnabled()) {
            return point.proceed();
        }
        Method method = getMethod(point);
        Object[] args = point.getArgs();
        String key = keyParser.parseKey(cache.key(), method, args, null);

        // 读缓存不支持通配符
        if (keyParser.isPattern(key)) {
            log.warn("@Cache key contains wildcard, skip cache read, key={}", key);
            return point.proceed();
        }

        // L1 -> L2 读取
        Object cached = getFromL1(key);
        if (cached == null) {
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                putToL1(key, cached);
            }
        }
        if (cached != null) {
            metricsService.recordHit(key);
            log.debug("@Cache hit, key={}", key);
            return unwrapNullValue(cached);
        }
        metricsService.recordMiss(key);

        // 布隆过滤器拦截
        if (hasBloomFilter(cache.bloomFilter()) && !bloomFilter.mightContain(cache.bloomFilter(), key)) {
            metricsService.recordBloomFilterBlock(key);
            log.debug("@Cache blocked by bloom filter, key={}", key);
            return null;
        }

        Object result = point.proceed();

        // 写入缓存
        if (shouldCache(cache.condition(), method, args, result, cache.cacheNull())) {
            Object valueToCache = result == null ? NullValue.INSTANCE : result;
            long expire = result == null ? resolveNullExpire(cache.nullExpire()) : cache.expire();
            writeToRedis(key, valueToCache, expire);
            putToL1(key, valueToCache);
            metricsService.recordPut(key);
            log.debug("@Cache set, key={}, expire={}s, nullValue={}", key, expire, result == null);

            // 写入布隆过滤器
            if (hasBloomFilter(cache.bloomFilter()) && result != null) {
                bloomFilter.put(cache.bloomFilter(), key);
            }
        }
        return result;
    }

    @Around("@annotation(refreshCache)")
    public Object aroundRefreshCache(ProceedingJoinPoint point, RefreshCache refreshCache) throws Throwable {
        Object result = point.proceed();
        if (!cacheProperties.isEnabled()) {
            return result;
        }
        Method method = getMethod(point);
        Object[] args = point.getArgs();

        String key = keyParser.parseKey(refreshCache.key(), method, args, result);
        if (keyParser.isPattern(key)) {
            log.warn("@RefreshCache key contains wildcard, skip refresh, key={}", key);
            return result;
        }
        if (!keyParser.parseCondition(refreshCache.condition(), method, args, result)) {
            return result;
        }
        if (result == null) {
            return result;
        }

        Runnable refreshAction = () -> {
            long expire = refreshCache.expire();
            writeToRedis(key, result, expire);
            putToL1(key, result);
            metricsService.recordRefresh(key);
            log.debug("@RefreshCache done, key={}, expire={}s", key, expire);
        };

        if (refreshCache.async()) {
            try {
                cacheRefreshExecutor.execute(refreshAction);
            } catch (Exception e) {
                log.error("@RefreshCache async submit failed, key={}", key, e);
            }
        } else {
            refreshAction.run();
        }
        return result;
    }

    @Around("@annotation(deleteCache)")
    public Object aroundDeleteCache(ProceedingJoinPoint point, DeleteCache deleteCache) throws Throwable {
        Object result = point.proceed();
        if (!cacheProperties.isEnabled()) {
            return result;
        }
        Method method = getMethod(point);
        Object[] args = point.getArgs();

        if (!keyParser.parseCondition(deleteCache.condition(), method, args, result)) {
            return result;
        }
        String key = keyParser.parseKey(deleteCache.key(), method, args, result);
        if (keyParser.isPattern(key)) {
            long deleted = redisService.deleteByPattern(key);
            localCacheManager.invalidateAll();
            metricsService.recordDelete(key);
            log.debug("@DeleteCache by pattern, key={}, deleted={}", key, deleted);
        } else {
            Boolean deleted = redisTemplate.delete(key);
            localCacheManager.invalidate(key);
            metricsService.recordDelete(key);
            log.debug("@DeleteCache done, key={}, deleted={}", key, deleted);
        }
        return result;
    }

    private Object getFromL1(String key) {
        if (!localCacheManager.isEnabled()) {
            return null;
        }
        Object value = localCacheManager.get(key);
        if (value != null) {
            log.debug("L1 cache hit in @Cache, key={}", key);
        }
        return value;
    }

    private void putToL1(String key, Object value) {
        localCacheManager.put(key, value);
    }

    private void writeToRedis(String key, Object value, long expireSeconds) {
        if (expireSeconds > 0) {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(expireSeconds));
        } else {
            redisTemplate.opsForValue().set(key, value);
        }
    }

    private Object unwrapNullValue(Object cached) {
        return cached instanceof NullValue ? null : cached;
    }

    private boolean shouldCache(String condition, Method method, Object[] args, Object result, boolean cacheNull) {
        if (!keyParser.parseCondition(condition, method, args, result)) {
            return false;
        }
        return result != null || cacheNull;
    }

    private long resolveNullExpire(long annotationNullExpire) {
        return annotationNullExpire > 0 ? annotationNullExpire : cacheProperties.getDefaultNullExpire();
    }

    private boolean hasBloomFilter(String bloomFilterName) {
        return bloomFilterName != null && !bloomFilterName.isBlank();
    }

    private Method getMethod(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        return signature.getMethod();
    }
}
