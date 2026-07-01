package online.yudream.base.infra.common.cache.metrics;

import lombok.RequiredArgsConstructor;
import online.yudream.base.infra.common.cache.prop.CacheProperties;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存统计服务。
 * <p>
 * 提供全局统计以及按 key 前缀聚合统计。
 */
@Service
@RequiredArgsConstructor
public class CacheMetricsService {

    private final CacheProperties cacheProperties;
    private final CacheMetrics totalMetrics = new CacheMetrics();
    private final Map<String, CacheMetrics> prefixMetrics = new ConcurrentHashMap<>();

    public CacheMetrics getTotalMetrics() {
        return totalMetrics;
    }

    public Map<String, CacheMetrics> getPrefixMetrics() {
        return Map.copyOf(prefixMetrics);
    }

    public void recordHit(String key) {
        if (!cacheProperties.getMetrics().isEnabled()) {
            return;
        }
        totalMetrics.incrementHit();
        prefixMetrics.computeIfAbsent(extractPrefix(key), k -> new CacheMetrics()).incrementHit();
    }

    public void recordMiss(String key) {
        if (!cacheProperties.getMetrics().isEnabled()) {
            return;
        }
        totalMetrics.incrementMiss();
        prefixMetrics.computeIfAbsent(extractPrefix(key), k -> new CacheMetrics()).incrementMiss();
    }

    public void recordPut(String key) {
        if (!cacheProperties.getMetrics().isEnabled()) {
            return;
        }
        totalMetrics.incrementPut();
        prefixMetrics.computeIfAbsent(extractPrefix(key), k -> new CacheMetrics()).incrementPut();
    }

    public void recordRefresh(String key) {
        if (!cacheProperties.getMetrics().isEnabled()) {
            return;
        }
        totalMetrics.incrementRefresh();
        prefixMetrics.computeIfAbsent(extractPrefix(key), k -> new CacheMetrics()).incrementRefresh();
    }

    public void recordDelete(String key) {
        if (!cacheProperties.getMetrics().isEnabled()) {
            return;
        }
        totalMetrics.incrementDelete();
        prefixMetrics.computeIfAbsent(extractPrefix(key), k -> new CacheMetrics()).incrementDelete();
    }

    public void recordBloomFilterBlock(String key) {
        if (!cacheProperties.getMetrics().isEnabled()) {
            return;
        }
        totalMetrics.incrementBloomFilterBlock();
        prefixMetrics.computeIfAbsent(extractPrefix(key), k -> new CacheMetrics()).incrementBloomFilterBlock();
    }

    public Stats getStats() {
        Stats stats = new Stats();
        stats.setTotal(new Stats.MetricsSnapshot(totalMetrics));
        if (cacheProperties.getMetrics().isAggregateByPrefix()) {
            prefixMetrics.forEach((prefix, metrics) -> stats.getByPrefix().put(prefix, new Stats.MetricsSnapshot(metrics)));
        }
        return stats;
    }

    private String extractPrefix(String key) {
        if (!cacheProperties.getMetrics().isAggregateByPrefix()) {
            return "default";
        }
        if (key == null || key.isEmpty()) {
            return "empty";
        }
        int firstColon = key.indexOf(':');
        int secondColon = key.indexOf(':', firstColon + 1);
        if (secondColon > 0) {
            return key.substring(0, secondColon);
        }
        if (firstColon > 0) {
            return key.substring(0, firstColon);
        }
        return key;
    }
}
