package online.yudream.base.infra.platform.milky.service;

public interface MilkyCredentialCipher {
    String encrypt(String plainText);
    String decrypt(String cipherText);
}
