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
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final String credentialKey;
    private final String legacyMilkyCredentialKey;
    private final SecureRandom random = new SecureRandom();

    public AesGcmMilkyCredentialCipher(
            @Value("${YUDREAM_CREDENTIAL_KEY:}") String credentialKey,
            @Value("${YUDREAM_MILKY_CREDENTIAL_KEY:}") String legacyMilkyCredentialKey) {
        this.credentialKey = credentialKey;
        this.legacyMilkyCredentialKey = legacyMilkyCredentialKey;
    }

    @Override
    public String encrypt(String plainText, Long connectionId) {
        try {
            return encrypt(plainText, requiredKey(credentialKey), aad(connectionId));
        } catch (Exception exception) {
            throw new IllegalStateException("Milky 凭证加密失败", exception);
        }
    }

    /**
     * 解密仅接受当前密钥 + 连接维度 AAD 绑定（新写入的密文格式）；
     * 兼容分支只保留「旧独立密钥」的历史数据回退，解密成功后调用方必须以
     * 当前密钥重新加密（save 时自动完成）。不再回退空 AAD，防止跨连接的密文互换。
     */
    @Override
    public String decrypt(String cipherText, Long connectionId) {
        Exception scopedFailure;
        try {
            return decrypt(cipherText, requiredKey(credentialKey), aad(connectionId));
        } catch (Exception exception) {
            scopedFailure = exception;
        }
        try {
            return decrypt(cipherText, legacyKey(), null);
        } catch (Exception exception) {
            exception.addSuppressed(scopedFailure);
            throw new IllegalStateException("Milky 凭证解密失败，请在连接配置中重新保存机器人凭据", exception);
        }
    }

    private String encrypt(String plainText, SecretKeySpec key, byte[] aad) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
        cipher.updateAAD(aad);
        return Base64.getEncoder().encodeToString(iv) + "."
                + Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    private String decrypt(String cipherText, SecretKeySpec key, byte[] aad) throws Exception {
        String[] parts = cipherText.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid ciphertext");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, Base64.getDecoder().decode(parts[0])));
        if (aad != null) {
            cipher.updateAAD(aad);
        }
        return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
    }

    private SecretKeySpec requiredKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("未配置 YUDREAM_CREDENTIAL_KEY");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) {
                throw new IllegalArgumentException("key must be 32 bytes");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("YUDREAM_CREDENTIAL_KEY 必须是 Base64 编码的 32 字节 AES 密钥", exception);
        }
    }

    private SecretKeySpec legacyKey() {
        if (legacyMilkyCredentialKey == null || legacyMilkyCredentialKey.isBlank()) {
            throw new IllegalStateException("未配置 YUDREAM_MILKY_CREDENTIAL_KEY");
        }
        try {
            byte[] key = Base64.getDecoder().decode(legacyMilkyCredentialKey);
            if (key.length != 32) {
                throw new IllegalArgumentException("invalid legacy key length");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("YUDREAM_MILKY_CREDENTIAL_KEY 必须是 Base64 编码的 32 字节 AES 密钥", exception);
        }
    }

    private byte[] aad(Long connectionId) {
        if (connectionId == null) {
            throw new IllegalArgumentException("Milky connectionId must not be null");
        }
        return ("milky:" + connectionId).getBytes(StandardCharsets.UTF_8);
    }
}
