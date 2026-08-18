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

    /**
     * 从任意标题（含中文）派生一个 ASCII 安全的页面 slug。
     * 保留小写字母、数字与连字符，其余字符折叠为连字符；结果为空时回退到随机短码。
     */
    public static String derive(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean lastDash = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                builder.append(c);
                lastDash = false;
            }
            else if (!lastDash && !builder.isEmpty()) {
                builder.append('-');
                lastDash = true;
            }
        }
        while (!builder.isEmpty() && builder.charAt(builder.length() - 1) == '-') {
            builder.setLength(builder.length() - 1);
        }
        String base = builder.toString();
        // 中文/无 ASCII 标题派生出的 slug 过短或为空时，追加稳定短 hash，避免冲突且保持可读前缀
        if (base.length() < 4) {
            base = base.isEmpty() ? "page" : base;
            base = base + "-" + Integer.toHexString(title == null ? "".hashCode() : title.hashCode());
        }
        return base;
    }
}
