package online.yudream.base.interfaces.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 保存已由全局异常处理器转换的请求失败信息，供 API 审计使用。
 */
public final class RequestFailureContext {

    static final String FAILURE_ATTR = RequestFailureContext.class.getName() + ".failure";

    private RequestFailureContext() {
    }

    public static void mark(HttpServletRequest request, Throwable exception) {
        if (request != null) {
            request.setAttribute(FAILURE_ATTR, exception == null ? "unknown" : exception.getClass().getSimpleName());
        }
    }

    public static String getSummary(HttpServletRequest request) {
        Object value = request.getAttribute(FAILURE_ATTR);
        return value instanceof String summary ? summary : null;
    }
}
