package online.yudream.base.infra.platform.milky.service;

public interface MilkyCredentialCipher {
    String encrypt(String plainText, Long connectionId);
    String decrypt(String cipherText, Long connectionId);
}
