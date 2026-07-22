package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.application.platform.render.cmd.MessageRenderCmd;
import online.yudream.base.application.platform.render.dto.RenderedImageDTO;
import online.yudream.base.application.platform.render.service.MessageRenderAppService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginRenderFrameworkServiceTest {

    @Test
    void rendersOnNamedExecutorAndKeepsCommandMenuSelector() {
        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicReference<MessageRenderCmd> received = new AtomicReference<>();
        MessageRenderAppService appService = new MessageRenderAppService(null, null) {
            @Override
            public RenderedImageDTO render(MessageRenderCmd cmd) {
                threadName.set(Thread.currentThread().getName());
                received.set(cmd);
                return new RenderedImageDTO("image/png", "image".getBytes(StandardCharsets.UTF_8), 100, 50);
            }
        };
        PluginRenderFrameworkService service = new PluginRenderFrameworkService(appService);

        var image = service.html("<div id='command-menu-card'>menu</div>").toCompletableFuture().join();
        service.shutdown();

        assertTrue(threadName.get().startsWith("plugin-render-"));
        assertEquals(Map.of("selector", "#command-menu-card"), received.get().getOptions());
        assertEquals("image/png", image.contentType());
        assertArrayEquals("image".getBytes(StandardCharsets.UTF_8), image.content());
    }
}
