package online.yudream.base.infra.common.cache.executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * 缓存异步刷新线程池配置。
 */
@Configuration
public class CacheRefreshExecutorConfig {

    @Bean(name = "cacheRefreshExecutor")
    public Executor cacheRefreshExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,
                8,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> new Thread(r, "cache-refresh-" + ThreadLocalRandom.current().nextInt(1000)),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
