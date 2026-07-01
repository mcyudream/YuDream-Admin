package online.yudream.base.infra.common.cache.bloom;

import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.infra.common.cache.prop.CacheProperties;
import online.yudream.base.infra.common.redis.RedisService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 基于 Redis Bitmap 的布隆过滤器。
 * <p>
 * 用于防止缓存穿透：在访问数据库前，先判断 key 是否可能存在于数据源中。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBloomFilter {

    private static final String BLOOM_KEY_PREFIX = "bloom:";

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisService redisService;
    private final CacheProperties cacheProperties;

    /**
     * 初始化布隆过滤器。
     *
     * @param name               过滤器名称
     * @param expectedInsertions 预计插入元素数量
     * @param fpp                期望误判率
     */
    public void init(String name, long expectedInsertions, double fpp) {
        long numBits = optimalNumOfBits(expectedInsertions, fpp);
        int numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
        String metaKey = metaKey(name);
        redisService.hSet(metaKey, "numBits", String.valueOf(numBits));
        redisService.hSet(metaKey, "numHashFunctions", String.valueOf(numHashFunctions));
        log.info("Bloom filter initialized, name={}, numBits={}, numHashFunctions={}", name, numBits, numHashFunctions);
    }

    /**
     * 添加元素。
     */
    public void put(String name, String element) {
        Meta meta = getMeta(name);
        if (meta == null) {
            log.warn("Bloom filter not initialized, name={}", name);
            return;
        }
        String bitmapKey = bitmapKey(name);
        long[] offsets = hash(element, meta.numBits, meta.numHashFunctions);
        for (long offset : offsets) {
            stringRedisTemplate.opsForValue().setBit(bitmapKey, offset, true);
        }
    }

    /**
     * 判断元素是否可能存在。
     *
     * @return true 可能存在，false 一定不存在
     */
    public boolean mightContain(String name, String element) {
        Meta meta = getMeta(name);
        if (meta == null) {
            // 未初始化时保守返回 true，避免误拦截
            return true;
        }
        String bitmapKey = bitmapKey(name);
        long[] offsets = hash(element, meta.numBits, meta.numHashFunctions);
        for (long offset : offsets) {
            Boolean bit = stringRedisTemplate.opsForValue().getBit(bitmapKey, offset);
            if (bit == null || !bit) {
                return false;
            }
        }
        return true;
    }

    /**
     * 删除布隆过滤器。
     */
    public void delete(String name) {
        redisService.delete(bitmapKey(name));
        redisService.delete(metaKey(name));
        log.info("Bloom filter deleted, name={}", name);
    }

    private Meta getMeta(String name) {
        String numBits = redisService.hGet(metaKey(name), "numBits");
        String numHashFunctions = redisService.hGet(metaKey(name), "numHashFunctions");
        if (numBits == null || numHashFunctions == null) {
            return null;
        }
        return new Meta(Long.parseLong(numBits), Integer.parseInt(numHashFunctions));
    }

    private long[] hash(String element, long numBits, int numHashFunctions) {
        byte[] bytes = Hashing.murmur3_128().hashString(element, StandardCharsets.UTF_8).asBytes();
        long hash1 = ((long) (bytes[0] & 0xFF) << 56)
                | ((long) (bytes[1] & 0xFF) << 48)
                | ((long) (bytes[2] & 0xFF) << 40)
                | ((long) (bytes[3] & 0xFF) << 32)
                | ((long) (bytes[4] & 0xFF) << 24)
                | ((long) (bytes[5] & 0xFF) << 16)
                | ((long) (bytes[6] & 0xFF) << 8)
                | ((long) (bytes[7] & 0xFF));
        long hash2 = ((long) (bytes[8] & 0xFF) << 56)
                | ((long) (bytes[9] & 0xFF) << 48)
                | ((long) (bytes[10] & 0xFF) << 40)
                | ((long) (bytes[11] & 0xFF) << 32)
                | ((long) (bytes[12] & 0xFF) << 24)
                | ((long) (bytes[13] & 0xFF) << 16)
                | ((long) (bytes[14] & 0xFF) << 8)
                | ((long) (bytes[15] & 0xFF));

        long[] offsets = new long[numHashFunctions];
        long combinedHash = hash1;
        for (int i = 0; i < numHashFunctions; i++) {
            offsets[i] = Math.abs(combinedHash % numBits);
            combinedHash += hash2;
        }
        return offsets;
    }

    private long optimalNumOfBits(long n, double p) {
        if (p == 0) {
            p = Double.MIN_VALUE;
        }
        return (long) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    private int optimalNumOfHashFunctions(long n, long m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private String bitmapKey(String name) {
        return cacheProperties.getKeyPrefix() + ":" + BLOOM_KEY_PREFIX + name + ":bits";
    }

    private String metaKey(String name) {
        return cacheProperties.getKeyPrefix() + ":" + BLOOM_KEY_PREFIX + name + ":meta";
    }

    private record Meta(long numBits, int numHashFunctions) {
    }
}
