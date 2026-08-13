package online.yudream.base.infra.system.log.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import online.yudream.base.domain.system.log.model.SystemLogLevel;
import online.yudream.base.infra.system.log.service.LogModuleResolver;
import online.yudream.base.infra.system.log.service.SystemLogBuffer;

import java.util.Map;

/**
 * 将宿主 logback 日志（含经 jul-to-slf4j 桥接进入的插件日志）写入系统日志内存环形缓冲，
 * 供系统日志页面实时查看与下载。
 */
public class SystemLogAppender extends AppenderBase<ILoggingEvent> {

    private final LogModuleResolver moduleResolver = new LogModuleResolver();

    @Override
    protected void append(ILoggingEvent event) {
        try {
            SystemLogLevel level = mapLevel(event.getLevel());
            String module = moduleResolver.resolve(event.getLoggerName());
            String traceId = mdckValue(event.getMDCPropertyMap(), "traceId");
            String throwable = throwable(event.getThrowableProxy());
            SystemLogBuffer.instance().append(event.getTimeStamp(), level, event.getLoggerName(), module,
                    event.getThreadName(), traceId, event.getFormattedMessage(), throwable);
        } catch (Exception ignored) {
            // 日志采集绝不能影响业务日志本身。
        }
    }

    private static SystemLogLevel mapLevel(Level level) {
        if (level == null) {
            return SystemLogLevel.INFO;
        }
        return switch (level.toInt()) {
            case Level.TRACE_INT -> SystemLogLevel.TRACE;
            case Level.DEBUG_INT -> SystemLogLevel.DEBUG;
            case Level.INFO_INT -> SystemLogLevel.INFO;
            case Level.WARN_INT -> SystemLogLevel.WARN;
            case Level.ERROR_INT -> SystemLogLevel.ERROR;
            default -> SystemLogLevel.INFO;
        };
    }

    private static String mdckValue(Map<String, String> mdc, String key) {
        if (mdc == null) {
            return null;
        }
        String value = mdc.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static String throwable(IThrowableProxy throwableProxy) {
        if (throwableProxy == null) {
            return null;
        }
        try {
            return ThrowableProxyUtil.asString(throwableProxy);
        } catch (Exception ignored) {
            return throwableProxy.getClassName() + ": " + throwableProxy.getMessage();
        }
    }
}
