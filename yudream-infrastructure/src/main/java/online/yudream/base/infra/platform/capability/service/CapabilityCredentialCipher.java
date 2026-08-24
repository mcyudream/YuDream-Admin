package online.yudream.base.infra.platform.capability.service;

import online.yudream.base.domain.common.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class CapabilityCredentialCipher {

    private static final String VERSION = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final byte[] NEO4J_PASSWORD_AAD = "neo4j:password".getBytes(StandardCharsets.UTF_8);

    private final String encodedKey;
    private final String legacyEncodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public CapabilityCredentialCipher(
            @Value("${yudream.credential.key:}") String encodedKey,
            @Value("${yudream.credential.capability-legacy-key:}") String legacyEncodedKey) {
        this.encodedKey = encodedKey;
        this.legacyEncodedKey = legacyEncodedKey;
    }

    public CapabilityCredentialCipher(String encodedKey) {
        this(encodedKey, "");
    }

    public String encryptNeo4jPassword(String password) {
        if (!StringUtils.hasText(password)) {
            return password;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, primaryKey(), new GCMParameterSpec(TAG_LENGTH, iv));
            cipher.updateAAD(NEO4J_PASSWORD_AAD);
            byte[] ciphertext = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Arrays.copyOf(iv, iv.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return VERSION + Base64.getEncoder().encodeToString(payload);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("Neo4j 凭据加密失败");
        }
    }

    public String decryptNeo4jPassword(String value) {
        if (!encrypted(value)) {
            return value;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(VERSION.length()));
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("invalid ciphertext");
            }
            for (SecretKey key : decryptionKeys()) {
                try {
                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, payload, 0, IV_LENGTH));
                    cipher.updateAAD(NEO4J_PASSWORD_AAD);
                    return new String(cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH), StandardCharsets.UTF_8);
                } catch (Exception ignored) {
                    // Try the legacy key only after the unified key cannot decrypt this ciphertext.
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to the uniform diagnostic below.
        }
        throw new BizException("Neo4j 凭据解密失败，请配置 YUDREAM_CREDENTIAL_KEY 后重试");
    }

    public boolean canEncrypt() {
        return primaryKeyOrNull() != null;
    }

    public boolean canDecrypt() {
        return !decryptionKeys().isEmpty();
    }

    public boolean encrypted(String value) {
        return StringUtils.hasText(value) && value.startsWith(VERSION);
    }

    private SecretKey primaryKey() {
        SecretKey key = primaryKeyOrNull();
        if (key == null) {
            throw new BizException("未配置 YUDREAM_CREDENTIAL_KEY，或其不是 Base64 编码的 32 字节 AES 密钥");
        }
        return key;
    }

    private SecretKey primaryKeyOrNull() {
        return decodeKey(encodedKey);
    }

    private java.util.List<SecretKey> decryptionKeys() {
        return java.util.stream.Stream.of(encodedKey, legacyEncodedKey)
                .map(this::decodeKey)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private SecretKey decodeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length == 32 ? new SecretKeySpec(decoded, "AES") : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
