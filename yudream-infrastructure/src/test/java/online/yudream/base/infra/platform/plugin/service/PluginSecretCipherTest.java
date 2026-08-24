package online.yudream.base.infra.platform.plugin.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginSecretCipherTest {
    private static final String CREDENTIAL_KEY = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
    private static final String LEGACY_KEY = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));

    @Test
    void encryptsAndDecryptsWithTheUnifiedCredentialKey() {
        PluginSecretCipher cipher = new PluginSecretCipher(CREDENTIAL_KEY);
        byte[] plain = "Bearer private-token".getBytes(StandardCharsets.UTF_8);

        var encrypted = cipher.encrypt("web-card", "access-token", plain);

        assertFalse(new String(encrypted.ciphertext(), StandardCharsets.UTF_8).contains("private-token"));
        assertArrayEquals(plain, cipher.decrypt("web-card", "access-token", encrypted.iv(), encrypted.ciphertext()));
    }

    @Test
    void bindsCiphertextToPluginAndSecretKeyScope() {
        PluginSecretCipher cipher = new PluginSecretCipher(CREDENTIAL_KEY);
        var encrypted = cipher.encrypt("web-card", "access-token", "secret".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt("another-plugin", "access-token", encrypted.iv(), encrypted.ciphertext()));
        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt("web-card", "another-key", encrypted.iv(), encrypted.ciphertext()));
    }

    @Test
    void decryptsHistoricalSecretsWithTheLegacyKeyButWritesWithTheUnifiedKey() throws Exception {
        var historical = encryptWithHistoricalPluginScope("web-card", "old-secret".getBytes(StandardCharsets.UTF_8));
        PluginSecretCipher migratingCipher = new PluginSecretCipher(CREDENTIAL_KEY, LEGACY_KEY);

        assertArrayEquals("old-secret".getBytes(StandardCharsets.UTF_8),
                migratingCipher.decrypt("web-card", "access-token", historical.iv(), historical.ciphertext()));

        var rewritten = migratingCipher.encrypt("web-card", "access-token", "new-secret".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class,
                () -> new PluginSecretCipher(LEGACY_KEY).decrypt(
                        "web-card", "access-token", rewritten.iv(), rewritten.ciphertext()));
    }

    @Test
    void doesNotPermitLegacyKeyForNewWrites() {
        PluginSecretCipher cipher = new PluginSecretCipher(null, LEGACY_KEY);

        assertThrows(IllegalStateException.class,
                () -> cipher.encrypt("web-card", "access-token", "secret".getBytes(StandardCharsets.UTF_8)));
    }

    private static PluginSecretCipher.Encrypted encryptWithHistoricalPluginScope(String pluginCode, byte[] plain) throws Exception {
        byte[] iv = new byte[12];
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(LEGACY_KEY), "AES"),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(pluginCode.getBytes(StandardCharsets.UTF_8));
        return new PluginSecretCipher.Encrypted(iv, cipher.doFinal(plain));
    }

    @Test
    void requiresA32ByteBase64Key() {
        assertThrows(IllegalStateException.class, () -> new PluginSecretCipher("short"));
    }
}
