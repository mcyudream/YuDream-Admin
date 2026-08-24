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
    private final byte[] credentialKey;
    private final String legacyEncodedKey;
    private final SecureRandom random = new SecureRandom();

    PluginSecretCipher(String credentialKey) {
        this(credentialKey, null);
    }

    PluginSecretCipher(String credentialKey, String legacyEncodedKey) {
        this.credentialKey = decodeKey(credentialKey, "YUDREAM_CREDENTIAL_KEY", false);
        this.legacyEncodedKey = legacyEncodedKey;
    }

    Encrypted encrypt(String pluginCode, String secretKey, byte[] plain) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        return new Encrypted(iv, crypt(Cipher.ENCRYPT_MODE, credentialKey(), aad(pluginCode, secretKey), iv, plain));
    }

    byte[] decrypt(String pluginCode, String secretKey, byte[] iv, byte[] ciphertext) {
        String aad = aad(pluginCode, secretKey);
        if (credentialKey != null) {
            try {
                return crypt(Cipher.DECRYPT_MODE, credentialKey, aad, iv, ciphertext);
            } catch (IllegalStateException primaryFailure) {
                if (!StringUtils.hasText(legacyEncodedKey)) {
                    throw primaryFailure;
                }
            }
        }
        return crypt(Cipher.DECRYPT_MODE, legacyKey(), legacyAad(pluginCode), iv, ciphertext);
    }

    private byte[] credentialKey() {
        if (credentialKey == null) {
            throw new IllegalStateException("YUDREAM_CREDENTIAL_KEY must be configured before writing plugin secrets");
        }
        return credentialKey;
    }

    private byte[] legacyKey() {
        return decodeKey(legacyEncodedKey, "YUDREAM_PLUGIN_SECRET_KEY", true);
    }

    private static String aad(String pluginCode, String secretKey) {
        return "plugin:" + pluginCode + ":" + secretKey;
    }

    private static String legacyAad(String pluginCode) {
        return pluginCode;
    }

    private static byte[] crypt(int mode, byte[] key, String aad, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(Arrays.copyOf(input, input.length));
        } catch (Exception e) {
            throw new IllegalStateException("Plugin secret operation failed", e);
        }
    }

    private static byte[] decodeKey(String encodedKey, String property, boolean required) {
        if (!StringUtils.hasText(encodedKey)) {
            if (required) {
                throw new IllegalStateException(property + " must be configured");
            }
            return null;
        }
        try {
            byte[] value = Base64.getDecoder().decode(encodedKey.trim());
            if (value.length != 32) {
                throw new IllegalStateException(property + " must decode to 32 bytes");
            }
            return value;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(property + " must be valid Base64", e);
        }
    }

    record Encrypted(byte[] iv, byte[] ciphertext) {
        Encrypted {
            iv = iv.clone();
            ciphertext = ciphertext.clone();
        }

        @Override
        public byte[] iv() {
            return iv.clone();
        }

        @Override
        public byte[] ciphertext() {
            return ciphertext.clone();
        }
    }
}
