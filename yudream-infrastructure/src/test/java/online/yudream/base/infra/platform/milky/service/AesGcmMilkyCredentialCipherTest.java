package online.yudream.base.infra.platform.milky.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmMilkyCredentialCipherTest {

    private static final String UNIFIED_KEY = key((byte) 1);
    private static final String LEGACY_KEY = key((byte) 2, 16);

    @Test
    void encryptsWithUnifiedKeyAndBindsCiphertextToConnection() {
        AesGcmMilkyCredentialCipher cipher = new AesGcmMilkyCredentialCipher(UNIFIED_KEY, LEGACY_KEY);

        String encrypted = cipher.encrypt("token", 100L);

        assertEquals("token", cipher.decrypt(encrypted, 100L));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(encrypted, 101L));
        assertThrows(Exception.class, () -> legacyDecrypt(encrypted, UNIFIED_KEY));
    }

    @Test
    void decryptsHistoricalNoAadCiphertextWithUnifiedKeyAfterMasterKeyUnification() throws Exception {
        AesGcmMilkyCredentialCipher cipher = new AesGcmMilkyCredentialCipher(UNIFIED_KEY, "");
        String legacyCiphertext = legacyEncrypt("historic-token", UNIFIED_KEY);

        assertEquals("historic-token", cipher.decrypt(legacyCiphertext, 100L));
    }

    @Test
    void decryptsAllHistoricalLegacyKeyLengthsAndRewritesWithUnifiedKey() throws Exception {
        for (int legacyKeyLength : new int[]{16, 24, 32}) {
            String legacyKey = key((byte) 2, legacyKeyLength);
            AesGcmMilkyCredentialCipher cipher = new AesGcmMilkyCredentialCipher(UNIFIED_KEY, legacyKey);
            String legacyCiphertext = legacyEncrypt("historic-token", legacyKey);

            assertEquals("historic-token", cipher.decrypt(legacyCiphertext, 100L));

            String rewritten = cipher.encrypt("historic-token", 100L);
            assertEquals("historic-token", cipher.decrypt(rewritten, 100L));
            assertThrows(Exception.class, () -> legacyDecrypt(rewritten, legacyKey));
        }
    }

    @Test
    void legacyKeyIsDecryptOnlyFallback() throws Exception {
        AesGcmMilkyCredentialCipher cipher = new AesGcmMilkyCredentialCipher("", LEGACY_KEY);
        String legacyCiphertext = legacyEncrypt("historic-token", LEGACY_KEY);

        assertEquals("historic-token", cipher.decrypt(legacyCiphertext, 100L));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> cipher.encrypt("new-token", 100L));
        assertTrue(exception.getCause().getMessage().contains("YUDREAM_CREDENTIAL_KEY"));
    }

    @Test
    void requiresBase64Encoded32ByteUnifiedKeyForWrites() {
        AesGcmMilkyCredentialCipher cipher = new AesGcmMilkyCredentialCipher(key((byte) 1, 16), LEGACY_KEY);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> cipher.encrypt("token", 100L));

        assertTrue(exception.getCause().getMessage().contains("Base64 编码的 32 字节 AES 密钥"));
    }

    private static String legacyEncrypt(String plainText, String encodedKey) throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(encodedKey), "AES"), new GCMParameterSpec(128, iv));
        return Base64.getEncoder().encodeToString(iv) + "."
                + Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private static String legacyDecrypt(String cipherText, String encodedKey) throws Exception {
        String[] parts = cipherText.split("\\.", -1);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(encodedKey), "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    }

    private static String key(byte value) {
        return key(value, 32);
    }

    private static String key(byte value, int length) {
        byte[] bytes = new byte[length];
        java.util.Arrays.fill(bytes, value);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
