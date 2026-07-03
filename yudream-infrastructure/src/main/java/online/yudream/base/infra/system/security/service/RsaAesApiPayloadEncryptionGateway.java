package online.yudream.base.infra.system.security.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.service.ApiPayloadEncryptionGateway;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class RsaAesApiPayloadEncryptionGateway implements ApiPayloadEncryptionGateway {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int RSA_KEY_SIZE = 2048;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final KeyPair keyPair = createKeyPair();

    @Override
    public String publicKey() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Override
    public byte[] decryptSessionKey(String encryptedSessionKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate());
            return cipher.doFinal(Base64.getDecoder().decode(encryptedSessionKey));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BizException("接口加密会话密钥解密失败");
        }
    }

    @Override
    public String decrypt(byte[] sessionKey, String iv, String encryptedPayload) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sessionKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(iv)));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(encryptedPayload));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BizException("接口请求体解密失败");
        }
    }

    @Override
    public String encrypt(byte[] sessionKey, String iv, String payload) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(iv)));
            byte[] encrypted = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new BizException("接口响应体加密失败");
        }
    }

    @Override
    public String newIv() {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
    }

    private KeyPair createKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(RSA_KEY_SIZE, secureRandom);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("接口加密密钥初始化失败", e);
        }
    }
}
