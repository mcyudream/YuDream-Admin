package online.yudream.base.infra.platform.milky.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AesGcmMilkyCredentialCipher implements MilkyCredentialCipher {
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();
    public AesGcmMilkyCredentialCipher(@Value("${YUDREAM_MILKY_CREDENTIAL_KEY:}") String encodedKey) { this.encodedKey = encodedKey; }
    @Override public String encrypt(String plainText) { return run(Cipher.ENCRYPT_MODE, plainText); }
    @Override public String decrypt(String cipherText) { return run(Cipher.DECRYPT_MODE, cipherText); }
    private String run(int mode, String value) {
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 16 && key.length != 24 && key.length != 32) throw new IllegalStateException("Milky AES 密钥长度无效");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            if (mode == Cipher.ENCRYPT_MODE) {
                byte[] iv = new byte[12]; random.nextBytes(iv); cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
                return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
            }
            String[] parts = value.split("\\.", -1); if (parts.length != 2) throw new IllegalArgumentException("invalid ciphertext");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new IllegalStateException("Milky 凭证加解密失败", exception); }
    }
}
