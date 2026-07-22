package online.yudream.base.infra.platform.plugin.service;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class PluginSecretCipherTest {
    private static final String KEY=Base64.getEncoder().encodeToString(new byte[32]);
    @Test void encryptsAndDecryptsWithoutPlaintextCiphertext(){PluginSecretCipher cipher=new PluginSecretCipher(KEY);byte[] plain="Bearer private-token".getBytes(StandardCharsets.UTF_8);var encrypted=cipher.encrypt("web-card",plain);assertFalse(new String(encrypted.ciphertext(),StandardCharsets.UTF_8).contains("private-token"));assertArrayEquals(plain,cipher.decrypt("web-card",encrypted.iv(),encrypted.ciphertext()));}
    @Test void bindsCiphertextToPluginScope(){PluginSecretCipher cipher=new PluginSecretCipher(KEY);var encrypted=cipher.encrypt("web-card","secret".getBytes(StandardCharsets.UTF_8));assertThrows(IllegalStateException.class,()->cipher.decrypt("another-plugin",encrypted.iv(),encrypted.ciphertext()));}
    @Test void requiresA32ByteBase64Key(){assertThrows(IllegalStateException.class,()->new PluginSecretCipher("short"));}
}
