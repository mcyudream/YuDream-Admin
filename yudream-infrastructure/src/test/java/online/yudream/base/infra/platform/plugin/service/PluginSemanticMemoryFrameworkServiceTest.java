package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryQuery;
import online.yudream.base.plugin.spi.system.memory.PluginSemanticMemoryRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginSemanticMemoryFrameworkServiceTest {

    @Test
    void unavailableServiceSkipsIndexingAndReturnsNoSearchHits() {
        var service = PluginSemanticMemoryFrameworkService.unavailable();

        service.index(new PluginSemanticMemoryRecord(
                "ai-chatbot:connection:group:user", "message-1", "hello", "provider", "model", Map.of()))
                .toCompletableFuture().join();

        var hits = service.search(new PluginSemanticMemoryQuery(
                "ai-chatbot:connection:group:user", "hello", "provider", "model", 5))
                .toCompletableFuture().join();

        assertFalse(service.status().available());
        assertTrue(hits.isEmpty());
    }
}
