package online.yudream.base.infra.platform.plugin.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.render.cmd.MessageRenderCmd;
import online.yudream.base.application.platform.render.dto.RenderedImageDTO;
import online.yudream.base.application.platform.render.service.MessageRenderAppService;
import online.yudream.base.domain.platform.render.model.RenderModels.SourceType;
import online.yudream.base.plugin.spi.system.render.PluginRenderService;
import online.yudream.base.plugin.spi.system.render.PluginRenderedImage;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class PluginRenderFrameworkService implements PluginRenderService {
    private final MessageRenderAppService messageRenderAppService;
    private final ExecutorService executor = new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), Thread.ofVirtual().name("plugin-render-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy());

    @Override
    public CompletionStage<PluginRenderedImage> html(String html) { return render(SourceType.HTML, html); }

    @Override
    public CompletionStage<PluginRenderedImage> html(String html, String selector) {
        return render(SourceType.HTML, html, selector);
    }

    @Override
    public CompletionStage<PluginRenderedImage> markdown(String markdown) { return render(SourceType.MARKDOWN, markdown); }

    @Override
    public CompletionStage<PluginRenderedImage> url(String url) { return render(SourceType.URL, url); }

    private CompletionStage<PluginRenderedImage> render(SourceType sourceType, String content) {
        String selector = sourceType == SourceType.HTML && content != null && content.contains("command-menu-card")
                ? "#command-menu-card" : null;
        return render(sourceType, content, selector);
    }

    private CompletionStage<PluginRenderedImage> render(SourceType sourceType, String content, String selector) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                MessageRenderCmd cmd = new MessageRenderCmd();
                cmd.setSourceType(sourceType);
                cmd.setContent(content);
                if (sourceType == SourceType.HTML && selector != null && !selector.isBlank()) {
                    cmd.setOptions(Map.of("selector", selector.trim()));
                }
                RenderedImageDTO rendered = messageRenderAppService.render(cmd);
                return new PluginRenderedImage(rendered.contentType(), rendered.content(), rendered.width(), rendered.height());
            }, executor).whenComplete((result, exception) -> {
                if (exception != null) {
                    log.error("Plugin render operation failed: sourceType={}, errorType={}",
                            sourceType, exception.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException exception) {
            log.error("Plugin render operation rejected: sourceType={}, errorType={}",
                    sourceType, exception.getClass().getSimpleName());
            return CompletableFuture.failedFuture(exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
