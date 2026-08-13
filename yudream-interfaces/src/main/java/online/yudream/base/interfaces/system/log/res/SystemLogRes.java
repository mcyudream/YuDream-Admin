package online.yudream.base.interfaces.system.log.res;

/**
 * 系统日志返回对象（面向管理页面）。
 */
public record SystemLogRes(
        long sequence,
        long timestamp,
        String time,
        String level,
        String module,
        String thread,
        String traceId,
        String logger,
        String message,
        String throwable) {
}
