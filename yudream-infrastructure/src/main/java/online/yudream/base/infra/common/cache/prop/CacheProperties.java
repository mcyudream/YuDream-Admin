package online.yudream.base.infra.common.cache.prop;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 缓存配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudream.cache")
public class CacheProperties {

    /**
     * 全局缓存 key 前缀。
     */
    private String keyPrefix = "yudream";

    /**
     * 是否启用缓存注解。
     */
    private boolean enabled = true;

    /**
     * 默认 null 值缓存过期时间（秒）。
     */
    private long defaultNullExpire = 60;

    /**
     * 本地一级缓存配置。
     */
    private L1Properties l1 = new L1Properties();

    /**
     * 统计监控配置。
     */
    private MetricsProperties metrics = new MetricsProperties();

    @Data
    public static class L1Properties {
        /**
         * 是否启用本地一级缓存。
         */
        private boolean enabled = false;

        /**
         * 本地缓存最大条目数。
         */
        private long maximumSize = 10000;

        /**
         * 本地缓存写入后过期时间（秒）。
         */
        private long expireAfterWrite = 60;
    }

    @Data
    public static class MetricsProperties {
        /**
         * 是否启用缓存统计。
         */
        private boolean enabled = true;

        /**
         * 是否按 key 前缀聚合统计。
         */
        private boolean aggregateByPrefix = true;
    }
}
