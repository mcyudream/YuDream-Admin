package online.yudream.base.infra.platform.plugin.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 沙盒插件日志桥接：QQ 沙盒执行作用域激活时，把插件包（online.yudream.base.plugin.*）
 * 的 WARN/ERROR 日志（含异常堆栈）追加到会话时间线。插件在 catch 里只记日志的失败
 * 也能在开发者工具里看到详细原因；生产链路作用域为空，直接透传。
 */
public class QqSandboxLogAppender extends AppenderBase<ILoggingEvent> {

    static final String PLUGIN_LOGGER_PREFIX = "online.yudream.base.plugin.";

    @Override
    protected void append(ILoggingEvent event) {
        try {
            QqSandboxSession session = QqSandboxExecutionScope.current();
            if (session == null || !session.acceptsCaptures()) {
                return;
            }
            if (event.getLevel() == null || event.getLevel().toInt() < Level.WARN_INT) {
                return;
            }
            String logger = event.getLoggerName();
            if (logger == null || !logger.startsWith(PLUGIN_LOGGER_PREFIX)) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("logger", logger);
            payload.put("thread", event.getThreadName());
            payload.put("message", event.getFormattedMessage());
            if (event.getThrowableProxy() != null) {
                payload.put("stackTrace", ThrowableProxyUtil.asString(event.getThrowableProxy()));
            }
            session.append("log", event.getLevel().toInt() >= Level.ERROR_INT ? "log.error" : "log.warn",
                    pluginCodeOf(logger), payload);
        } catch (Exception ignored) {
            // 日志桥接绝不能影响日志主链路
        }
    }

    /** 从 logger 名推导插件编码：online.yudream.base.plugin.{code}.Xxx → {code}。 */
    static String pluginCodeOf(String logger) {
        String remainder = logger.substring(PLUGIN_LOGGER_PREFIX.length());
        int dot = remainder.indexOf('.');
        return dot > 0 ? remainder.substring(0, dot) : remainder;
    }
}
