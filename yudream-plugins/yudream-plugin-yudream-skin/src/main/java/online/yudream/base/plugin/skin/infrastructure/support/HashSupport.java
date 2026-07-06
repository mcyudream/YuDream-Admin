package online.yudream.base.plugin.skin.infrastructure.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public class HashSupport {

    private HashSupport() {
    }

    public static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest("SHA-256", bytes));
    }

    public static String playerUuid(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("YuDreamSkin:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
        return uuid.toString().replace("-", "");
    }

    private static byte[] digest(String algorithm, byte[] bytes) {
        try {
            return MessageDigest.getInstance(algorithm).digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " 不可用", e);
        }
    }
}
