package online.yudream.base.infra.system.security.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RsaAesApiPayloadEncryptionGatewayTest {

    private static final OAEPParameterSpec RSA_OAEP_PARAMETER_SPEC = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    @Test
    void decryptsBrowserCompatibleRsaOaepSessionKey() throws Exception {
        RsaAesApiPayloadEncryptionGateway gateway = new RsaAesApiPayloadEncryptionGateway();
        KeyPair keyPair = keyPairOf(gateway);
        byte[] sessionKey = "01234567890123456789012345678901".getBytes(StandardCharsets.US_ASCII);

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), RSA_OAEP_PARAMETER_SPEC);
        String encryptedSessionKey = Base64.getEncoder().encodeToString(cipher.doFinal(sessionKey));

        assertArrayEquals(sessionKey, gateway.decryptSessionKey(encryptedSessionKey));
    }

    @Test
    void encryptsAndDecryptsAesGcmPayload() {
        RsaAesApiPayloadEncryptionGateway gateway = new RsaAesApiPayloadEncryptionGateway();
        byte[] sessionKey = "01234567890123456789012345678901".getBytes(StandardCharsets.US_ASCII);
        String iv = gateway.newIv();
        String plain = "api-encryption-regression";

        String encrypted = gateway.encrypt(sessionKey, iv, plain);

        assertEquals(plain, gateway.decrypt(sessionKey, iv, encrypted));
    }

    private static KeyPair keyPairOf(RsaAesApiPayloadEncryptionGateway gateway) throws Exception {
        Field field = RsaAesApiPayloadEncryptionGateway.class.getDeclaredField("keyPair");
        field.setAccessible(true);
        return (KeyPair) field.get(gateway);
    }
}
