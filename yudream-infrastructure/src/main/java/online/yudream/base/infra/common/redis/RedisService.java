package online.yudream.base.infra.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.common.redis.exception.RedisCacheException;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis 常用工具箱。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    /* ==================== String 操作 ==================== */

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, long expireSeconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(expireSeconds));
    }

    public Boolean setNx(String key, String value, long expireSeconds) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(expireSeconds));
    }

    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    public Boolean expire(String key, long expireSeconds) {
        return stringRedisTemplate.expire(key, Duration.ofSeconds(expireSeconds));
    }

    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key);
    }

    public Long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    public Long decrement(String key, long delta) {
        return stringRedisTemplate.opsForValue().decrement(key, delta);
    }

    /* ==================== Hash 操作 ==================== */

    public String hGet(String key, String field) {
        return stringRedisTemplate.<String, String>opsForHash().get(key, field);
    }

    public void hSet(String key, String field, String value) {
        stringRedisTemplate.<String, String>opsForHash().put(key, field, value);
    }

    public void hSet(String key, Map<String, String> map) {
        stringRedisTemplate.<String, String>opsForHash().putAll(key, map);
    }

    public Long hDelete(String key, Object... fields) {
        return stringRedisTemplate.<String, String>opsForHash().delete(key, fields);
    }

    public Boolean hHasKey(String key, String field) {
        return stringRedisTemplate.<String, String>opsForHash().hasKey(key, field);
    }

    public Map<String, String> hGetAll(String key) {
        return stringRedisTemplate.<String, String>opsForHash().entries(key);
    }

    public Set<String> hKeys(String key) {
        return stringRedisTemplate.<String, String>opsForHash().keys(key);
    }

    public List<String> hValues(String key) {
        return stringRedisTemplate.<String, String>opsForHash().values(key);
    }

    /* ==================== List 操作 ==================== */

    public Long lPush(String key, String... values) {
        return stringRedisTemplate.opsForList().leftPushAll(key, values);
    }

    public Long rPush(String key, String... values) {
        return stringRedisTemplate.opsForList().rightPushAll(key, values);
    }

    public String lPop(String key) {
        return stringRedisTemplate.opsForList().leftPop(key);
    }

    public String rPop(String key) {
        return stringRedisTemplate.opsForList().rightPop(key);
    }

    public List<String> lRange(String key, long start, long end) {
        return stringRedisTemplate.opsForList().range(key, start, end);
    }

    public Long lIndex(String key, long index, String value) {
        return stringRedisTemplate.opsForList().remove(key, index, value);
    }

    /* ==================== Set 操作 ==================== */

    public Long sAdd(String key, String... members) {
        return stringRedisTemplate.opsForSet().add(key, members);
    }

    public Set<String> sMembers(String key) {
        return stringRedisTemplate.opsForSet().members(key);
    }

    public Boolean sIsMember(String key, String member) {
        return stringRedisTemplate.opsForSet().isMember(key, member);
    }

    public Long sRemove(String key, Object... members) {
        return stringRedisTemplate.opsForSet().remove(key, members);
    }

    /* ==================== ZSet 操作 ==================== */

    public Boolean zAdd(String key, String member, double score) {
        return stringRedisTemplate.opsForZSet().add(key, member, score);
    }

    public Set<String> zRange(String key, long start, long end) {
        return stringRedisTemplate.opsForZSet().range(key, start, end);
    }

    public Set<String> zRangeByScore(String key, double min, double max) {
        return stringRedisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    public Long zRemove(String key, Object... members) {
        return stringRedisTemplate.opsForZSet().remove(key, members);
    }

    /* ==================== 批量/匹配删除 ==================== */

    /**
     * 根据通配符模式安全删除所有匹配 Key（使用 SCAN，避免 KEYS 阻塞）。
     *
     * @param pattern 匹配模式，如 {@code user:info:*}
     * @return 删除的 key 数量
     */
    public long deleteByPattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return 0L;
        }
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.error("scan redis keys error, pattern={}", pattern, e);
            throw new RedisCacheException("scan redis keys error", e);
        }
        if (keys.isEmpty()) {
            return 0L;
        }
        return Optional.ofNullable(delete(keys)).orElse(0L);
    }

    /* ==================== 分布式锁 ==================== */

    private static final String LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    /**
     * 尝试获取分布式锁。
     *
     * @param lockKey    锁 key
     * @param requestId  请求标识，用于安全释放
     * @param expireSeconds 锁过期时间（秒）
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, String requestId, long expireSeconds) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, requestId, expireSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放分布式锁（仅当 value 匹配时才释放）。
     *
     * @param lockKey   锁 key
     * @param requestId 请求标识
     * @return 是否释放成功
     */
    public boolean releaseLock(String lockKey, String requestId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LOCK_SCRIPT);
        script.setResultType(Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(lockKey), requestId);
        return result != null && result == 1L;
    }

    /* ==================== 通用工具 ==================== */

    public DataType type(String key) {
        return stringRedisTemplate.type(key);
    }

    public Long dbSize() {
        return stringRedisTemplate.execute((RedisCallback<Long>) connection -> connection.serverCommands().dbSize());
    }

    public Set<String> keys(String pattern) {
        return stringRedisTemplate.keys(pattern);
    }

    public void flushDb() {
        stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }
}
