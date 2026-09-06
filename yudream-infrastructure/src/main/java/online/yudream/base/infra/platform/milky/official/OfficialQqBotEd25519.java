package online.yudream.base.infra.platform.milky.official;

import online.yudream.base.domain.common.exception.BizException;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HexFormat;

/**
 * 官方机器人 Webhook 使用 Ed25519：secret 重复填充到 32 字节作为种子。
 */
final class OfficialQqBotEd25519 {
    private static final byte[] PKCS8_PREFIX = HexFormat.of().parseHex("302e020100300506032b657004220420");

    private OfficialQqBotEd25519() { }

    static String sign(String secret, byte[] message) {
        try {
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey(secret));
            signature.update(message);
            return HexFormat.of().formatHex(signature.sign());
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            BizException failure = new BizException("官方机器人回调签名失败");
            failure.initCause(exception);
            throw failure;
        }
    }

    static String signValidation(String secret, String eventTs, String plainToken) {
        return sign(secret, (nvl(eventTs) + nvl(plainToken)).getBytes(StandardCharsets.UTF_8));
    }

    static boolean verify(String secret, String timestamp, String body, String hexSignature) {
        if (blank(hexSignature) || blank(timestamp)) {
            return false;
        }
        try {
            String expected = sign(secret, (nvl(timestamp) + nvl(body)).getBytes(StandardCharsets.UTF_8));
            return constantTimeEquals(expected, hexSignature.trim());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int diff = 0;
        for (int index = 0; index < left.length(); index++) {
            diff |= Character.toLowerCase(left.charAt(index)) ^ Character.toLowerCase(right.charAt(index));
        }
        return diff == 0;
    }

    private static PrivateKey privateKey(String secret) throws Exception {
        byte[] encoded = new byte[PKCS8_PREFIX.length + 32];
        System.arraycopy(PKCS8_PREFIX, 0, encoded, 0, PKCS8_PREFIX.length);
        System.arraycopy(seed(secret), 0, encoded, PKCS8_PREFIX.length, 32);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    static byte[] seed(String secret) {
        byte[] source = nvl(secret).getBytes(StandardCharsets.UTF_8);
        if (source.length == 0) {
            throw new BizException("官方机器人 AppSecret 不能为空");
        }
        byte[] seed = new byte[32];
        int copied = 0;
        while (copied < 32) {
            int length = Math.min(source.length, 32 - copied);
            System.arraycopy(source, 0, seed, copied, length);
            copied += length;
        }
        return seed;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
