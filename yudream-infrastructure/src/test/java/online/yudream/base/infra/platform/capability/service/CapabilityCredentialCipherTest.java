package online.yudream.base.infra.platform.capability.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapabilityCredentialCipherTest {

    private static final String CREDENTIAL_KEY = key((byte) 1);
    private static final String LEGACY_KEY = key((byte) 2);

    @Test
    void encryptsNeo4jPasswordWithUnifiedKeyWithoutPersistingPlaintext() {
        CapabilityCredentialCipher cipher = new CapabilityCredentialCipher(CREDENTIAL_KEY);

        String encrypted = cipher.encryptNeo4jPassword("neo4j-password");

        assertFalse(encrypted.contains("neo4j-password"));
        assertEquals("neo4j-password", cipher.decryptNeo4jPassword(encrypted));
    }

    @Test
    void decryptsLegacyCiphertextButWritesOnlyWithUnifiedKey() {
        CapabilityCredentialCipher legacyCipher = new CapabilityCredentialCipher(LEGACY_KEY);
        String historicalCiphertext = legacyCipher.encryptNeo4jPassword("historic-password");
        CapabilityCredentialCipher migratingCipher = new CapabilityCredentialCipher(CREDENTIAL_KEY, LEGACY_KEY);

        assertEquals("historic-password", migratingCipher.decryptNeo4jPassword(historicalCiphertext));

        String rewritten = migratingCipher.encryptNeo4jPassword("historic-password");
        assertThrows(RuntimeException.class, () -> legacyCipher.decryptNeo4jPassword(rewritten));
    }

    @Test
    void doesNotAllowLegacyKeyToEncryptNewPasswords() {
        CapabilityCredentialCipher cipher = new CapabilityCredentialCipher("", LEGACY_KEY);

        assertThrows(RuntimeException.class, () -> cipher.encryptNeo4jPassword("neo4j-password"));
    }

    @Test
    void rejectsCiphertextWhoseAuthenticatedPayloadWasModified() {
        CapabilityCredentialCipher cipher = new CapabilityCredentialCipher(CREDENTIAL_KEY);
        String encrypted = cipher.encryptNeo4jPassword("neo4j-password");
        String tampered = encrypted.substring(0, encrypted.length() - 1) + "A";

        assertThrows(RuntimeException.class, () -> cipher.decryptNeo4jPassword(tampered));
    }

    @Test
    void rejectsNonBase64OrNon32ByteUnifiedMasterKeyWhenUsed() {
        CapabilityCredentialCipher cipher = new CapabilityCredentialCipher("not-a-valid-key");

        assertThrows(RuntimeException.class, () -> cipher.encryptNeo4jPassword("neo4j-password"));
    }

    private static String key(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
