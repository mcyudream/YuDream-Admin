package online.yudream.base.infra.platform.capability.mapper;

import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CapabilityModuleInfraMapperTest {

    private static final CapabilityCredentialCipher CIPHER = new CapabilityCredentialCipher(
            Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptsOnlyNeo4jPasswordAtRestAndRestoresItForRuntime() {
        CapabilityModule module = module("neo4j", Map.of(
                "uri", "bolt://neo4j:7687", "username", "neo4j", "password", "private-password", "database", "neo4j"));

        var stored = CapabilityModuleInfraMapper.toDataObj(module, CIPHER);
        var restored = CapabilityModuleInfraMapper.toDomain(stored, CIPHER);

        assertFalse(stored.getConfig().get("password").contains("private-password"));
        assertEquals("private-password", restored.getConfig().get("password"));
        assertEquals("bolt://neo4j:7687", restored.getConfig().get("uri"));
    }

    @Test
    void readsLegacyPlaintextButEncryptsItOnNextSave() {
        var stored = CapabilityModuleInfraMapper.toDataObj(module("neo4j", Map.of(
                "uri", "bolt://neo4j:7687", "username", "neo4j", "password", "private-password", "database", "neo4j")), CIPHER);
        stored.getConfig().put("password", "legacy-plaintext");

        CapabilityModule restored = CapabilityModuleInfraMapper.toDomain(stored, CIPHER);
        var rewritten = CapabilityModuleInfraMapper.toDataObj(restored, CIPHER);

        assertEquals("legacy-plaintext", restored.getConfig().get("password"));
        assertFalse(rewritten.getConfig().get("password").contains("legacy-plaintext"));
    }

    @Test
    void preservesLegacyPasswordWhenNoKeyIsAvailableSoDescriptorSyncCanContinue() {
        CapabilityCredentialCipher unavailableCipher = new CapabilityCredentialCipher("");
        CapabilityModule module = module("neo4j", Map.of("password", "legacy-plaintext"));

        var stored = CapabilityModuleInfraMapper.toDataObj(module, unavailableCipher);
        CapabilityModule restored = CapabilityModuleInfraMapper.toDomain(stored, unavailableCipher);

        assertEquals("legacy-plaintext", stored.getConfig().get("password"));
        assertEquals("legacy-plaintext", restored.getConfig().get("password"));
    }

    @Test
    void restoresHistoricalNeo4jPasswordCiphertextOnRead() {
        var stored = CapabilityModuleInfraMapper.toDataObj(module("neo4j", Map.of("password", "x")), CIPHER);
        stored.getConfig().put("password", CIPHER.encryptNeo4jPassword("historic-password"));

        CapabilityModule restored = CapabilityModuleInfraMapper.toDomain(stored, CIPHER);

        assertEquals("historic-password", restored.getConfig().get("password"));
    }

    @Test
    void leavesNonNeo4jCapabilityPasswordsUntouched() {
        CapabilityModule module = module("rabbitmq", Map.of("password", "rabbit-secret"));

        var stored = CapabilityModuleInfraMapper.toDataObj(module, CIPHER);

        assertEquals("rabbit-secret", stored.getConfig().get("password"));
    }

    private static CapabilityModule module(String code, Map<String, String> config) {
        return CapabilityModule.builder()
                .code(code)
                .name(code)
                .type(CapabilityType.GRAPH)
                .config(config)
                .build();
    }
}
