package online.yudream.base.infra.platform.plugin.service;

import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

final class PluginSecretCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    PluginSecretCipher(String encodedKey) { this.key = decodeKey(encodedKey); }
    Encrypted encrypt(String scope, byte[] plain) { byte[] iv = new byte[IV_BYTES]; random.nextBytes(iv); return new Encrypted(iv, crypt(Cipher.ENCRYPT_MODE, scope, iv, plain)); }
    byte[] decrypt(String scope, byte[] iv, byte[] ciphertext) { return crypt(Cipher.DECRYPT_MODE, scope, iv, ciphertext); }
    private byte[] crypt(int mode, String scope, byte[] iv, byte[] input) {
        try { Cipher cipher=Cipher.getInstance(TRANSFORMATION); cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(TAG_BITS,iv)); cipher.updateAAD(scope.getBytes(StandardCharsets.UTF_8)); return cipher.doFinal(Arrays.copyOf(input,input.length)); }
        catch(Exception e){throw new IllegalStateException("Plugin secret operation failed",e);}
    }
    private static byte[] decodeKey(String encodedKey) {
        if(!StringUtils.hasText(encodedKey))throw new IllegalStateException("yudream.plugin.secret-key must be configured");
        try{byte[] value=Base64.getDecoder().decode(encodedKey.trim());if(value.length!=32)throw new IllegalStateException("yudream.plugin.secret-key must decode to 32 bytes");return value;}catch(IllegalArgumentException e){throw new IllegalStateException("yudream.plugin.secret-key must be valid Base64",e);}
    }
    record Encrypted(byte[] iv,byte[] ciphertext){Encrypted{iv=iv.clone();ciphertext=ciphertext.clone();}@Override public byte[] iv(){return iv.clone();}@Override public byte[] ciphertext(){return ciphertext.clone();}}
}
