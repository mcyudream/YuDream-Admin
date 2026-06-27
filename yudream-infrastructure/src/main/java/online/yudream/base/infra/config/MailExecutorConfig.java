package online.yudream.base.infra.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class MailExecutorConfig {

    @Bean(name = "mailExecutor")
    public Executor mailExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,                      // 核心线程数
                5,                      // 最大线程数
                60L, TimeUnit.SECONDS,  // 空闲线程存活时间
                new LinkedBlockingQueue<>(100), // 队列容量
                r -> new Thread(r, "mail-sender-" + ThreadLocalRandom.current().nextInt(1000)),
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：由调用线程执行
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}