package online.yudream.base.interfaces.platform.wiki.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 公开 Wiki 问答专用执行器。
 *
 * <p>公开入口由 {@code PublicWikiChatRateLimiter} 按「IP + spaceSlug」限制并发，此处再用一个全局有界线程池兜底，
 * 防止单机同时堆积过多公开 LLM 流式任务。默认最大并发与限流器的 {@code max-concurrent=2} 保持一致，
 * 队列容量为 0（直接交接），超过容量立即拒绝，拒绝策略明确为 AbortPolicy。</p>
 */
@Configuration
public class PublicWikiChatExecutorConfig {

    @Bean(name = "publicWikiChatExecutor")
    public ThreadPoolTaskExecutor publicWikiChatExecutor(
            @Value("${yudream.platform.wiki.chat.public-executor.core-size:2}") int corePoolSize,
            @Value("${yudream.platform.wiki.chat.public-executor.max-size:2}") int maxPoolSize,
            @Value("${yudream.platform.wiki.chat.public-executor.queue-capacity:0}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("public-wiki-chat-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
