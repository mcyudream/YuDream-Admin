package online.yudream.base.domain.valobj;

import online.yudream.base.domain.common.exception.BizException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 十六进制颜色工具：统一 6 位 hex 的校验与规范化。
 * <p>
 * 规范形态为 {@code #rrggbb} 小写；入参允许省略 {@code #}，空值表示未设置。
 */
public final class HexColor {

    /** 合法 hex 正则：可省略 #，允许空串（视为未设置），供接口层请求校验引用。 */
    public static final String PATTERN = "^(?:#?[0-9a-fA-F]{6})?$";

    private static final Pattern HEX = Pattern.compile(PATTERN);

    private HexColor() {
    }

    /**
     * 规范化 hex 颜色：空值 → {@code null}；合法 hex → {@code #rrggbb} 小写；其它抛领域异常。
     */
    public static String normalize(String value) {
        if (isUnset(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!HEX.matcher(trimmed).matches()) {
            throw new BizException("颜色值必须为 #rrggbb 十六进制格式: " + value);
        }
        String body = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        return "#" + body.toLowerCase(Locale.ROOT);
    }

    /** 空值与空白串统一视为未设置。 */
    public static boolean isUnset(String value) {
        return value == null || value.isBlank();
    }
}
