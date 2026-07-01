package online.yudream.base.infra.common.cache.metrics;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存统计视图。
 */
@Data
public class Stats {

    private MetricsSnapshot total;
    private Map<String, MetricsSnapshot> byPrefix = new HashMap<>();

    @Data
    public static class MetricsSnapshot {
        private long hit;
        private long miss;
        private long put;
        private long refresh;
        private long delete;
        private long bloomFilterBlock;
        private double hitRate;

        public MetricsSnapshot() {
        }

        public MetricsSnapshot(CacheMetrics metrics) {
            this.hit = metrics.getHitCount();
            this.miss = metrics.getMissCount();
            this.put = metrics.getPutCount();
            this.refresh = metrics.getRefreshCount();
            this.delete = metrics.getDeleteCount();
            this.bloomFilterBlock = metrics.getBloomFilterBlockCount();
            this.hitRate = metrics.getHitRate();
        }
    }
}
