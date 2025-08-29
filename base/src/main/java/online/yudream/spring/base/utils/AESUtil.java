package online.yudream.spring.base.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AESUtil {
    private static final String ALGORITHM = "AES";
    @Value("${base.security.transformation}")
    private String transformation;

    @Value("${base.security.secret-key}")
    private String secretKey;

    public String encrypt(String plaintext) {
        try {
            String key = String.format("%-16s", this.secretKey).replace(' ', '0'); // 填充到 16 字节
            Cipher cipher = Cipher.getInstance(transformation);
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }
}