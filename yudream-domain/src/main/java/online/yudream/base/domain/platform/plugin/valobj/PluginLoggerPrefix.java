package online.yudream.base.domain.platform.plugin.valobj;

/**
 * 插件日志命名空间：插件包结构约定为 online.yudream.base.plugin.{segment}.*，
 * 从 mainClass 截取根包后的第一段作为日志过滤前缀；第三方未遵循约定的插件兜底用 根包+编码。
 * 与沙盒日志桥（按 logger 首段推导插件编码）使用同一约定。
 */
public final class PluginLoggerPrefix {

    public static final String ROOT = "online.yudream.base.plugin.";

    private PluginLoggerPrefix() {
    }

    public static String of(String mainClass, String code) {
        if (mainClass != null && mainClass.startsWith(ROOT)) {
            int next = mainClass.indexOf('.', ROOT.length());
            if (next > ROOT.length()) {
                return mainClass.substring(0, next);
            }
        }
        return ROOT + code;
    }
}
