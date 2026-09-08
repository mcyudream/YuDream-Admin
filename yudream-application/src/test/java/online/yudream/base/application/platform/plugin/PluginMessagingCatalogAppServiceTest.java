package online.yudream.base.application.platform.plugin;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.plugin.dto.PluginMessagingConnectionDTO;
import online.yudream.base.application.platform.plugin.service.PluginMessagingCatalogAppService;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageResult;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingConnection;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingGroup;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMessagingCatalogAppServiceTest {

    @Test
    void returnsEmptyWhenMessagingCapabilityDisabled() {
        AtomicInteger calls = new AtomicInteger();
        PluginMessagingCatalogAppService service = new PluginMessagingCatalogAppService(
                new ToggleCapabilityAppService(false),
                new StubMessagingService(calls, List.of(), List.of()));

        assertTrue(service.connections().isEmpty());
        assertTrue(service.groups("9").isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void mapsOfficialProtocolForPluginPickers() {
        PluginMessagingCatalogAppService service = new PluginMessagingCatalogAppService(
                new ToggleCapabilityAppService(true),
                new StubMessagingService(new AtomicInteger(),
                        List.of(new PluginMessagingConnection("9", "官Q", "qq", null, "official")),
                        List.of()));

        List<PluginMessagingConnectionDTO> connections = service.connections();

        assertEquals(1, connections.size());
        assertEquals("9", connections.getFirst().getId());
        assertEquals("官Q", connections.getFirst().getName());
        assertEquals("qq", connections.getFirst().getPlatform());
        assertEquals("official", connections.getFirst().getProtocol());
    }

    @Test
    void returnsEmptyGroupsWhenConnectionIdBlank() {
        AtomicInteger calls = new AtomicInteger();
        PluginMessagingCatalogAppService service = new PluginMessagingCatalogAppService(
                new ToggleCapabilityAppService(true),
                new StubMessagingService(calls, List.of(), List.of()));

        assertTrue(service.groups(" ").isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void mapsGroupsForSelectedConnection() {
        PluginMessagingCatalogAppService service = new PluginMessagingCatalogAppService(
                new ToggleCapabilityAppService(true),
                new StubMessagingService(new AtomicInteger(), List.of(),
                        List.of(new PluginMessagingGroup("openid-1", "活动群"))));

        assertEquals(1, service.groups("9").size());
        assertEquals("openid-1", service.groups("9").getFirst().getId());
        assertEquals("活动群", service.groups("9").getFirst().getName());
    }

    private static final class ToggleCapabilityAppService extends CapabilityAppService {
        private final boolean enabled;

        private ToggleCapabilityAppService(boolean enabled) {
            super(null, List.of());
            this.enabled = enabled;
        }

        @Override
        public boolean enabled(String code) {
            return enabled;
        }
    }

    private static final class StubMessagingService implements PluginMessagingService {
        private final AtomicInteger calls;
        private final List<PluginMessagingConnection> connections;
        private final List<PluginMessagingGroup> groups;

        private StubMessagingService(AtomicInteger calls,
                                     List<PluginMessagingConnection> connections,
                                     List<PluginMessagingGroup> groups) {
            this.calls = calls;
            this.connections = connections;
            this.groups = groups;
        }

        @Override
        public List<PluginMessagingConnection> connections() {
            calls.incrementAndGet();
            return connections;
        }

        @Override
        public List<PluginMessagingGroup> groups(String connectionId) {
            calls.incrementAndGet();
            return groups;
        }

        @Override
        public CompletionStage<PluginMessageResult> send(PluginMessageRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<PluginMessageResult> sendDirectToBoundUser(String userId, PluginMessageContent content) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletionStage<PluginMessageResult> sendToChannel(String connectionId, String channelId, PluginMessageContent content) {
            throw new UnsupportedOperationException();
        }
    }
}
