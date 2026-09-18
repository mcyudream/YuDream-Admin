package online.yudream.base.application.platform.seo;

import java.util.List;
import java.util.Locale;

/**
 * 公开页 SEO 的路径解析规则：纯函数，便于单测。
 */
public final class SeoPathRules {

    private SeoPathRules() {
    }

    /**
     * 归一路径：去掉查询串与片段、合并重复斜杠、去掉末尾斜杠（根路径保留 "/"）。
     * 输入通常来自 nginx 的 $uri（已解码归一）或 ?path= 直连参数。
     */
    public static String normalizePath(String raw) {
        String path = raw == null || raw.isBlank() ? "/" : raw.trim();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        StringBuilder normalized = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            normalized.append('/').append(segment);
        }
        return normalized.length() == 0 ? "/" : normalized.toString();
    }

    /**
     * 根路径与配置的公开内容前缀视为公开页，其余（后台、认证、支付等）按非公开处理。
     */
    public static boolean isPublicPath(String normalizedPath, List<String> publicPrefixes) {
        if ("/".equals(normalizedPath)) {
            return true;
        }
        if (publicPrefixes == null) {
            return false;
        }
        return publicPrefixes.stream().anyMatch(prefix ->
                normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
    }

    /**
     * 取路径中某前缀之后的剩余段，如 subPath("/site/about/team", "/site") -> "about/team"。
     */
    public static String subPath(String normalizedPath, String prefix) {
        if (normalizedPath.equals(prefix)) {
            return "";
        }
        if (normalizedPath.length() <= prefix.length() + 1) {
            return "";
        }
        return normalizedPath.substring(prefix.length() + 1);
    }

    /**
     * 解析逗号分隔的公开前缀配置，统一为小写并以 "/" 开头。
     */
    public static List<String> parsePrefixes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(item -> item.startsWith("/") ? item : "/" + item)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .toList();
    }
}
