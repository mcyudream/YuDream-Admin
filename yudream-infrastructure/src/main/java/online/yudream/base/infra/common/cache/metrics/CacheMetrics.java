package online.yudream.base.infra.common.cache.metrics;

import lombok.Data;

import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存统计指标。
 */
@Data
public class CacheMetrics {

    private final LongAdder cacheHit = new LongAdder();
    private final LongAdder cacheMiss = new LongAdder();
    private final LongAdder cachePut = new LongAdder();
    private final LongAdder cacheRefresh = new LongAdder();
    private final LongAdder cacheDelete = new LongAdder();
    private final LongAdder bloomFilterBlock = new LongAdder();

    public void incrementHit() {
        cacheHit.increment();
    }

    public void incrementMiss() {
        cacheMiss.increment();
    }

    public void incrementPut() {
        cachePut.increment();
    }

    public void incrementRefresh() {
        cacheRefresh.increment();
    }

    public void incrementDelete() {
        cacheDelete.increment();
    }

    public void incrementBloomFilterBlock() {
        bloomFilterBlock.increment();
    }

    public long getHitCount() {
        return cacheHit.sum();
    }

    public long getMissCount() {
        return cacheMiss.sum();
    }

    public long getPutCount() {
        return cachePut.sum();
    }

    public long getRefreshCount() {
        return cacheRefresh.sum();
    }

    public long getDeleteCount() {
        return cacheDelete.sum();
    }

    public long getBloomFilterBlockCount() {
        return bloomFilterBlock.sum();
    }

    public double getHitRate() {
        long total = getHitCount() + getMissCount();
        return total == 0 ? 0.0 : (double) getHitCount() / total;
    }
}
