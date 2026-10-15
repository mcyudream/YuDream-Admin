package online.yudream.base.domain.system.user.valobj;

import java.util.Locale;

/**
 * 角色的语义默认色：内置角色按 code 取固定代表色，自定义角色统一兜底色。
 */
public final class RoleColorDefaults {

    public static final String SUPER_ADMIN = "#ef4444";
    public static final String ADMIN = "#f59e0b";
    public static final String USER = "#3b82f6";
    public static final String GUEST = "#94a3b8";
    /** 未命中任何语义的兜底色。 */
    public static final String FALLBACK = "#64748b";

    private RoleColorDefaults() {
    }

    /** 角色 code（不区分大小写）→ 语义 hex。 */
    public static String forCode(String code) {
        if (code == null || code.isBlank()) {
            return FALLBACK;
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "SUPER_ADMIN" -> SUPER_ADMIN;
            case "ADMIN" -> ADMIN;
            case "USER" -> USER;
            case "GUEST" -> GUEST;
            default -> FALLBACK;
        };
    }
}
