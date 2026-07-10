package online.yudream.base.infra.platform.satori.service;

import online.yudream.base.domain.platform.satori.service.SatoriCredentialCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AesGcmSatoriCredentialCipher implements SatoriCredentialCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmSatoriCredentialCipher(@Value("${YUDREAM_SATORI_CREDENTIAL_KEY:}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    private SecretKey key() {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encodedKey); } catch (IllegalArgumentException ex) { throw new IllegalStateException("YUDREAM_SATORI_CREDENTIAL_KEY 必须是 Base64 编码的 AES 密钥", ex); }
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) throw new IllegalStateException("YUDREAM_SATORI_CREDENTIAL_KEY 必须是 128、192 或 256 位 AES 密钥");
        return new SecretKeySpec(bytes, "AES");
    }

    @Override public String encrypt(String plaintext) {
        SecretKey secretKey = key();
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES]; secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) { throw new IllegalStateException("Satori 凭证加密失败", ex); }
    }

    @Override public String decrypt(String ciphertext) {
        SecretKey secretKey = key();
        try {
            String[] values = ciphertext.split("\\.", -1); if (values.length != 2) throw new IllegalArgumentException("invalid ciphertext");
            Cipher cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, Base64.getDecoder().decode(values[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(values[1])), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Satori 凭证解密失败", ex); }
    }
}
