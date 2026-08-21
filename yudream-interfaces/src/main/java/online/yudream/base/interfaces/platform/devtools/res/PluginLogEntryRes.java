package online.yudream.base.interfaces.platform.devtools.res;

/**
 * 插件运行日志返回对象（开发者工具日志流页）。
 */
public record PluginLogEntryRes(
        long sequence,
        long timestamp,
        String time,
        String level,
        String logger,
        String thread,
        String traceId,
        String message,
        String throwable) {
}
