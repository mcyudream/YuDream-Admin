package online.yudream.base.domain.platform.plugin.valobj;

import java.util.Locale;

public record PluginHttpEndpointInfo(
        String pluginCode,
        String method,
        String path,
        String fullPath,
        String permission,
        boolean wrapResult
) {
    public PluginHttpEndpointInfo(String pluginCode, String method, String path, String permission, boolean wrapResult) {
        this(
                pluginCode,
                normalizeMethod(method),
                normalizePath(path),
                fullPath(pluginCode, path),
                normalizeText(permission),
                wrapResult
        );
    }

    private static String normalizeMethod(String method) {
        return hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : "*";
    }

    private static String normalizePath(String path) {
        if (!hasText(path)) {
            return "/";
        }
        String normalized = path.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String normalizeText(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static String fullPath(String pluginCode, String path) {
        String normalizedPath = normalizePath(path);
        String pluginRoot = "/api/plugins/" + pluginCode;
        return "/".equals(normalizedPath) ? pluginRoot : pluginRoot + normalizedPath;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
