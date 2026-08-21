package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.platform.milky.sandbox.QqSandboxSession;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 沙盒错误诊断：把插件处理器/分发链路逃逸出的异常以结构化负载追加到会话时间线，
 * 让开发者工具能直接看到异常类型、消息与完整堆栈，而不是只有一句泛化提示。
 */
final class QqSandboxDiagnostics {

    private QqSandboxDiagnostics() {
    }

    /** 仅在沙盒执行作用域激活且会话仍接收捕获时追加时间线事件；生产链路零开销透传。 */
    static void appendError(String action, String pluginCode, Throwable error, Map<String, Object> context) {
        QqSandboxSession session = QqSandboxExecutionScope.current();
        if (session == null || !session.acceptsCaptures()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (context != null) {
            context.forEach((key, value) -> {
                if (value != null) {
                    payload.put(key, value);
                }
            });
        }
        payload.put("errorType", error.getClass().getName());
        payload.put("message", error.getMessage() == null ? "" : error.getMessage());
        payload.put("stackTrace", stackTrace(error));
        session.append("runtime", action, pluginCode, payload);
    }

    static String stackTrace(Throwable error) {
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
