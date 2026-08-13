package online.yudream.base.domain.system.log.model;

/**
 * 一条系统运行日志。由宿主日志采集器（内存环形缓冲 + logback appender）产出，
 * 覆盖宿主 SLF4J 日志以及经 jul-to-slf4j 桥接进入宿主日志体系的插件日志。
 */
public record SystemLogEntry(
        long sequence,
        long timestamp,
        SystemLogLevel level,
        String logger,
        String module,
        String thread,
        String traceId,
        String message,
        String throwable) {

    public SystemLogEntry {
        level = level == null ? SystemLogLevel.INFO : level;
        logger = logger == null ? "" : logger;
        module = module == null || module.isBlank() ? "系统" : module;
        thread = thread == null ? "" : thread;
        message = message == null ? "" : message;
    }
}
