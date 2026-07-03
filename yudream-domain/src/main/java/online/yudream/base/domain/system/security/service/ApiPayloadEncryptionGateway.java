package online.yudream.base.domain.system.security.service;

public interface ApiPayloadEncryptionGateway {

    String publicKey();

    byte[] decryptSessionKey(String encryptedSessionKey);

    String decrypt(byte[] sessionKey, String iv, String encryptedPayload);

    String encrypt(byte[] sessionKey, String iv, String payload);

    String newIv();
}
