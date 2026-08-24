package online.yudream.base.infra.platform.graph.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityStatus;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jCapabilityProviderTest {

    private static final CapabilityCredentialCipher CIPHER = new CapabilityCredentialCipher(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void enablesUsingTheCapabilityManagedPhysicalConnectionConfiguration() {
        RecordingGateway gateway = new RecordingGateway();
        Neo4jCapabilityProvider provider = new Neo4jCapabilityProvider(gateway, CIPHER);
        Map<String, String> config = Map.of(
                "uri", "bolt://neo4j:7687", "username", "neo4j", "password", "private-password", "database", "neo4j");

        provider.enable(config);

        assertEquals(config, gateway.reconfigured.get());
        assertEquals(CapabilityStatus.ENABLED, provider.health().status());
    }

    @Test
    void reportsCredentialErrorWhenCiphertextCannotBeRestored() {
        Neo4jCapabilityProvider provider = new Neo4jCapabilityProvider(new RecordingGateway(), CIPHER);

        assertThrows(IllegalStateException.class, () -> provider.enable(Map.of("password", "v1:unreadable")));
        assertEquals(CapabilityStatus.ERROR, provider.health().status());
    }

    @Test
    void reportsCredentialErrorForHistoricalPlaintextWithoutAnyKey() {
        Neo4jCapabilityProvider provider = new Neo4jCapabilityProvider(
                new RecordingGateway(), new CapabilityCredentialCipher(""));

        assertThrows(IllegalStateException.class, () -> provider.enable(Map.of("password", "legacy-plaintext")));
        assertEquals(CapabilityStatus.ERROR, provider.health().status());
    }

    @Test
    void declaresAllPhysicalConnectionFieldsIncludingPassword() {
        Map<String, String> defaults = new Neo4jCapabilityProvider(new RecordingGateway(), CIPHER).descriptor().defaultConfig();

        assertTrue(defaults.containsKey("uri"));
        assertTrue(defaults.containsKey("username"));
        assertTrue(defaults.containsKey("password"));
        assertTrue(defaults.containsKey("database"));
    }

    private static final class RecordingGateway extends Neo4jGraphDatabaseGateway {
        private final AtomicReference<Map<String, String>> reconfigured = new AtomicReference<>();

        @Override
        public synchronized void reconfigure(Map<String, String> config) {
            reconfigured.set(Map.copyOf(config));
        }
    }
}
