package online.yudream.base.plugin.authlib.infrastructure.service;

import online.yudream.base.plugin.authlib.domain.valobj.AuthlibKeyPair;
import online.yudream.base.plugin.authlib.infrastructure.repository.AuthlibRepository;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class AuthlibCryptoService {

    private final AuthlibRepository repository;

    public AuthlibCryptoService(AuthlibRepository repository) {
        this.repository = repository;
    }

    public AuthlibKeyPair keyPair() {
        return repository.keyPair().orElseGet(this::generateAndSave);
    }

    public String publicKeyPem() {
        String base64 = keyPair().publicKey();
        StringBuilder builder = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (int index = 0; index < base64.length(); index += 76) {
            builder.append(base64, index, Math.min(index + 76, base64.length())).append('\n');
        }
        return builder.append("-----END PUBLIC KEY-----\n").toString();
    }

    public String sign(String value) {
        try {
            byte[] privateBytes = Base64.getDecoder().decode(keyPair().privateKey());
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(privateKey);
            signature.update(value.getBytes());
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("材质属性签名失败：" + e.getMessage(), e);
        }
    }

    private AuthlibKeyPair generateAndSave() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(4096);
            KeyPair keyPair = generator.generateKeyPair();
            return repository.saveKeyPair(new AuthlibKeyPair(
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded())
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Authlib RSA 密钥生成失败：" + e.getMessage(), e);
        }
    }
}
