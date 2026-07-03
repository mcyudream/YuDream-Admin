package online.yudream.base.interfaces.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.interfaces.common.config.WebLogProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Web 请求耗时拦截器。
 * <p>
 * 以多行、缩进、带前缀的方式输出请求开始与结束信息，方便阅读。
 */
@Slf4j
@RequiredArgsConstructor
public class WebInvokeTimeInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "WEB_LOG_START_TIME";

    private final WebLogProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled()) {
            return true;
        }
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        String p = prefix();
        String url = request.getMethod() + " " + request.getRequestURI();
        String params = formatParams(request);

        log.info("{} ▶ 开始请求\n{}   URL   : {}\n{}   参数  : {}",
                p, p, url, p, params);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (!properties.isEnabled()) {
            return;
        }
        Long start = (Long) request.getAttribute(START_TIME_ATTR);
        long cost = start == null ? 0 : System.currentTimeMillis() - start;

        String p = prefix();
        String url = request.getMethod() + " " + request.getRequestURI();
        String params = formatParams(request);
        String body = formatBody(request);

        log.info("{} ◀ 结束请求\n{}   URL   : {}\n{}   参数  : {}\n{}   请求体: {}\n{}   耗时  : {}ms",
                p, p, url, p, params, p, body, p, cost);
    }

    private String prefix() {
        return "[" + properties.getPrefix() + "]";
    }

    private String formatParams(HttpServletRequest request) {
        String query = request.getQueryString();
        if (StringUtils.hasText(query)) {
            return query;
        }
        Map<String, String[]> paramMap = request.getParameterMap();
        if (!paramMap.isEmpty()) {
            return paramMap.toString();
        }
        return "无";
    }

    private String formatBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                try {
                    String encoding = wrapper.getCharacterEncoding();
                    String body = new String(buf, StringUtils.hasText(encoding) ? encoding : "UTF-8");
                    return StringUtils.hasText(body) ? body : "无";
                }
                catch (UnsupportedEncodingException e) {
                    return "[读取请求体失败]";
                }
            }
        }
        return "无";
    }
}
