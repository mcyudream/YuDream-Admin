package online.yudream.base.domain.system.user.valobj;

/**
 * 部门的语义默认色：按部门名称关键词取固定主色，未命中统一兜底色。
 */
public final class DeptColorDefaults {

    public static final String TECH = "#3b82f6";
    public static final String FINANCE = "#f59e0b";
    public static final String HR = "#8b5cf6";
    public static final String MARKET = "#ec4899";
    public static final String TEACHING = "#10b981";
    /** 未命中任何关键词的兜底色。 */
    public static final String FALLBACK = "#64748b";

    private static final String[] TECH_KEYWORDS = {"技术", "研发", "开发", "运维", "工程"};
    private static final String[] FINANCE_KEYWORDS = {"财务", "审计", "会计"};
    private static final String[] HR_KEYWORDS = {"人事", "行政", "人力", "办公室"};
    private static final String[] MARKET_KEYWORDS = {"市场", "运营", "宣传", "媒体"};
    private static final String[] TEACHING_KEYWORDS = {"教学", "教务", "学生", "学术"};

    private DeptColorDefaults() {
    }

    /** 部门名称 → 语义 hex；名称命中多组关键词时按技术/财务/人事/市场/教学的固定顺序取先命中者。 */
    public static String forName(String name) {
        if (name == null || name.isBlank()) {
            return FALLBACK;
        }
        String normalized = name.trim();
        if (matches(normalized, TECH_KEYWORDS)) {
            return TECH;
        }
        if (matches(normalized, FINANCE_KEYWORDS)) {
            return FINANCE;
        }
        if (matches(normalized, HR_KEYWORDS)) {
            return HR;
        }
        if (matches(normalized, MARKET_KEYWORDS)) {
            return MARKET;
        }
        if (matches(normalized, TEACHING_KEYWORDS)) {
            return TEACHING;
        }
        return FALLBACK;
    }

    private static boolean matches(String name, String[] keywords) {
        for (String keyword : keywords) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
