package online.yudream.base.domain.system.user.valobj;

import online.yudream.base.domain.valobj.HexColor;

import java.util.Locale;
import java.util.Map;

/**
 * 角色代表色：自由 hex（{@code #rrggbb} 小写，可空；null 表示未设置）。
 * <p>
 * 兼容历史固定语义键（info/success/warning/danger/emphasis/neutral），旧数据加载与
 * 旧客户端提交都按语义 hex 落库。
 */
public final class RoleColor {

    /** 合法 hex 正则，供接口层请求校验引用。 */
    public static final String HEX_PATTERN = HexColor.PATTERN;

    /** 历史固定语义键 → 语义 hex。 */
    private static final Map<String, String> LEGACY_KEYS = Map.of(
            "info", "#3b82f6",
            "success", "#22c55e",
            "warning", "#f59e0b",
            "danger", "#ef4444",
            "emphasis", "#8b5cf6",
            "neutral", "#94a3b8");

    private RoleColor() {
    }

    /**
     * 规范化角色代表色：空 → {@code null}；历史语义键 → 语义 hex；合法 hex → 小写；其它抛领域异常。
     */
    public static String normalize(String value) {
        if (HexColor.isUnset(value)) {
            return null;
        }
        String legacy = LEGACY_KEYS.get(value.trim().toLowerCase(Locale.ROOT));
        return legacy == null ? HexColor.normalize(value) : legacy;
    }
}
