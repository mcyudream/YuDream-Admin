package online.yudream.base.domain.platform.wiki.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.Locale;
import java.util.regex.Pattern;

public record WikiSlug(String value) {

    private static final Pattern PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public static WikiSlug of(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!PATTERN.matcher(normalized).matches()) {
            throw new BizException("知识库路径仅支持小写字母、数字和连字符");
        }
        return new WikiSlug(normalized);
    }
}
