package online.yudream.base.domain.platform.satori.service;

/** Encrypts connection credentials at the infrastructure boundary. */
public interface SatoriCredentialCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
