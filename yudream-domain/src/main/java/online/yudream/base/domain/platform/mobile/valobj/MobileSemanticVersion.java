package online.yudream.base.domain.platform.mobile.valobj;

/**
 * 移动端宿主版本的宽松语义化比较，纯函数便于单测。
 * <p>
 * 与市场契约的严格 {@code SemVer} 不同：App 上报的 hostVersion 与插件声明的
 * minHostVersion 允许省略段位（"1.2" 等价 "1.2.0"），允许预发布后缀
 * （"1.2.0-beta.1" 低于 "1.2.0"）。解析失败按"无法校验"处理，交由调用方决定放行。
 */
public final class MobileSemanticVersion implements Comparable<MobileSemanticVersion> {

    private final int[] core;
    private final String preRelease;

    private MobileSemanticVersion(int[] core, String preRelease) {
        this.core = core;
        this.preRelease = preRelease;
    }

    /**
     * 宽松解析；null/空白/非法格式返回 empty，由调用方回落"无法校验即放行"。
     */
    public static MobileSemanticVersion parseLenient(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            return null;
        }
        String corePart = trimmed;
        String preRelease = null;
        int dash = trimmed.indexOf('-');
        if (dash >= 0) {
            corePart = trimmed.substring(0, dash);
            preRelease = trimmed.substring(dash + 1);
            if (preRelease.isEmpty() || !preRelease.matches("[0-9A-Za-z.-]+")) {
                return null;
            }
        }
        if (corePart.isEmpty() || !corePart.matches("[0-9]+(\\.[0-9]+)*")) {
            return null;
        }
        String[] segments = corePart.split("\\.");
        int[] core = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            try {
                core[i] = Integer.parseInt(segments[i]);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return new MobileSemanticVersion(core, preRelease);
    }

    /**
     * hostVersion 是否不低于 minHostVersion。任一侧无法解析（含空白）视为未声明、放行。
     */
    public static boolean satisfiesHostVersion(String hostVersion, String minHostVersion) {
        MobileSemanticVersion host = parseLenient(hostVersion);
        MobileSemanticVersion min = parseLenient(minHostVersion);
        if (host == null || min == null) {
            return true;
        }
        return host.compareTo(min) >= 0;
    }

    @Override
    public int compareTo(MobileSemanticVersion other) {
        int length = Math.max(core.length, other.core.length);
        for (int i = 0; i < length; i++) {
            int left = i < core.length ? core[i] : 0;
            int right = i < other.core.length ? other.core[i] : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        // 语义化版本规则：带预发布后缀的版本低于同核心的正式版本
        if (preRelease == null && other.preRelease == null) {
            return 0;
        }
        if (preRelease == null) {
            return 1;
        }
        if (other.preRelease == null) {
            return -1;
        }
        return comparePreRelease(preRelease, other.preRelease);
    }

    private static int comparePreRelease(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int length = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < length; i++) {
            if (i >= leftParts.length) {
                return -1;
            }
            if (i >= rightParts.length) {
                return 1;
            }
            int compared = comparePreReleasePart(leftParts[i], rightParts[i]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private static int comparePreReleasePart(String left, String right) {
        if (left.equals(right)) {
            return 0;
        }
        boolean leftNumeric = left.matches("[0-9]+");
        boolean rightNumeric = right.matches("[0-9]+");
        if (leftNumeric && rightNumeric) {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        }
        if (leftNumeric) {
            return -1;
        }
        if (rightNumeric) {
            return 1;
        }
        return left.compareTo(right);
    }
}
