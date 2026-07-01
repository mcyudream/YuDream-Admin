package online.yudream.base.infra.common.redis.exception;

/**
 * Redis 缓存层运行时异常。
 */
public class RedisCacheException extends RuntimeException {

    public RedisCacheException(String message) {
        super(message);
    }

    public RedisCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
