package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.wiki.enumerate.WikiSpaceCoverType;

import java.security.SecureRandom;

/**
 * 空间封面值对象。
 * <p>
 * SEEDED 封面通过稳定 seed 确定性呈现（同一 seed 渲染结果稳定，不得每次刷新随机跳变）；
 * UPLOAD 封面必须是当前用户归属且公开可读的图片文件对象。
 */
public record WikiCover(WikiSpaceCoverType type, String seed, Long fileObjectId, String alt) {

    public static final WikiCover NONE = new WikiCover(WikiSpaceCoverType.NONE, "", null, "");

    private static final int MAX_SEED_LENGTH = 64;
    private static final int MAX_ALT_LENGTH = 200;
    private static final String SEED_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public WikiCover {
        type = WikiSpaceCoverType.of(type);
        seed = seed == null ? "" : seed.trim();
        alt = alt == null ? "" : alt.trim();
        if (alt.length() > MAX_ALT_LENGTH) {
            throw new BizException("封面替代文本不能超过 " + MAX_ALT_LENGTH + " 个字符");
        }
        if (type == WikiSpaceCoverType.SEEDED) {
            seed = normalizeSeed(seed);
            fileObjectId = null;
        }
        else if (type == WikiSpaceCoverType.UPLOAD) {
            if (fileObjectId == null) {
                throw new BizException("上传封面必须提供文件对象");
            }
            seed = "";
        }
        else {
            seed = "";
            fileObjectId = null;
            alt = "";
        }
    }

    /** 随机封面：服务端生成 seed，客户端不可指定展示内容。 */
    public static WikiCover random(String alt) {
        return new WikiCover(WikiSpaceCoverType.SEEDED, randomSeed(), null, alt);
    }

    public static WikiCover seeded(String seed, String alt) {
        return new WikiCover(WikiSpaceCoverType.SEEDED, seed, null, alt);
    }

    public static WikiCover upload(Long fileObjectId, String alt) {
        return new WikiCover(WikiSpaceCoverType.UPLOAD, "", fileObjectId, alt);
    }

    public static String randomSeed() {
        StringBuilder builder = new StringBuilder(12);
        for (int index = 0; index < 12; index++) {
            builder.append(SEED_ALPHABET.charAt(RANDOM.nextInt(SEED_ALPHABET.length())));
        }
        return builder.toString();
    }

    private static String normalizeSeed(String seed) {
        if (seed == null || seed.isBlank()) {
            throw new BizException("随机封面缺少稳定 seed");
        }
        String normalized = seed.trim().toLowerCase();
        if (normalized.length() > MAX_SEED_LENGTH) {
            throw new BizException("封面 seed 不能超过 " + MAX_SEED_LENGTH + " 个字符");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (SEED_ALPHABET.indexOf(normalized.charAt(index)) < 0) {
                throw new BizException("封面 seed 只允许小写字母与数字");
            }
        }
        return normalized;
    }
}
