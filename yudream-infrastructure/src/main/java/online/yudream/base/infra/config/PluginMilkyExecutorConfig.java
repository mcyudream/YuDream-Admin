package online.yudream.base.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class PluginMilkyExecutorConfig {

    @Bean(name = "pluginMilkyExecutor")
    public Executor pluginMilkyExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,
                8,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                r -> new Thread(r, "milky-plugin-" + System.nanoTime()),
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}
