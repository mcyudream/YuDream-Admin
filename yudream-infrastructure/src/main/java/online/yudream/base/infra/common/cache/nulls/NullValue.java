package online.yudream.base.infra.common.cache.nulls;

import java.io.Serializable;

/**
 * 缓存 null 值哨兵对象。
 * <p>
 * 由于 Redis 无法直接存储 {@code null}，使用单例哨兵代替，读取时识别并返回真正的 {@code null}。
 */
public final class NullValue implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final NullValue INSTANCE = new NullValue();

    private NullValue() {
    }

    private Object readResolve() {
        return INSTANCE;
    }

    @Override
    public String toString() {
        return "NullValue";
    }
}
