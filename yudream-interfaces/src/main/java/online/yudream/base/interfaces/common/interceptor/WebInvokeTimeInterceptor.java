package online.yudream.base.interfaces.common.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.system.monitor.service.SystemMonitorAppService;
import online.yudream.base.domain.system.monitor.dto.ApiLogDTO;
import online.yudream.base.interfaces.common.RequestFailureContext;
import online.yudream.base.interfaces.common.config.WebLogProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.UnsupportedEncodingException;

@Slf4j
@RequiredArgsConstructor
public class WebInvokeTimeInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR = "WEB_LOG_START_TIME";
    private static final int MAX_BODY_LENGTH = 2000;
    private static final java.util.regex.Pattern CONTROL_CHARS = java.util.regex.Pattern.compile("\\p{Cntrl}");

    private final WebLogProperties properties;
    private final SystemMonitorAppService systemMonitorAppService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled()) {
            return true;
        }
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());

        String p = prefix();
        String url = sanitize(request.getMethod() + " " + request.getRequestURI());
        String params = formatParams(request);
        log.info("{} > start request\n{}   URL   : {}\n{}   params: {}", p, p, url, p, params);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!properties.isEnabled()) {
            return;
        }
        Long start = (Long) request.getAttribute(START_TIME_ATTR);
        long cost = start == null ? 0 : System.currentTimeMillis() - start;

        String p = prefix();
        String url = sanitize(request.getMethod() + " " + request.getRequestURI());
        String params = formatParams(request);
        String body = formatBody(request);
        log.info("{} < end request\n{}   URL   : {}\n{}   params: {}\n{}   body  : {}\n{}   cost  : {}ms",
                p, p, url, p, params, p, body, p, cost);
        recordApiLog(request, response, ex, cost, body);
    }

    private String prefix() {
        return "[" + properties.getPrefix() + "]";
    }

    private String formatParams(HttpServletRequest request) {
        String query = request.getQueryString();
        return StringUtils.hasText(query) ? maskSensitive(query) : "none";
    }

    private String formatBody(HttpServletRequest request) {
        if (request.getRequestURI().matches("/api/platform/agents/[^/]+/(?:run|debug/stream)")) {
            return "[Agent 运行载荷已省略]";
        }
        if (request instanceof ContentCachingRequestWrapper wrapper) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                try {
                    String encoding = wrapper.getCharacterEncoding();
                    String body = new String(buf, StringUtils.hasText(encoding) ? encoding : "UTF-8");
                    return StringUtils.hasText(body) ? maskSensitive(body) : "none";
                }
                catch (UnsupportedEncodingException e) {
                    return "[body read failed]";
                }
            }
        }
        return "none";
    }

    private void recordApiLog(HttpServletRequest request, HttpServletResponse response, Exception ex, long cost, String body) {
        if (isLogClearRequest(request)) {
            return;
        }
        String handledFailure = RequestFailureContext.getSummary(request);
        String errorSummary = handledFailure != null ? handledFailure : ex == null ? null : ex.getClass().getSimpleName();
        try {
            systemMonitorAppService.recordApiLog(ApiLogDTO.builder()
                    .method(sanitize(request.getMethod()))
                    .path(sanitize(request.getRequestURI()))
                    .query(maskSensitive(sanitize(request.getQueryString())))
                    .requestBody(limit(body))
                    .status(response.getStatus())
                    .costMs(cost)
                    .success(handledFailure == null && ex == null && response.getStatus() < 400)
                    .loginId(currentLoginId())
                    .ip(sanitize(clientIp(request)))
                    .userAgent(sanitize(request.getHeader("User-Agent")))
                    .errorMessage(errorSummary == null ? null : sanitize(errorSummary))
                    .build());
        }
        catch (Exception auditException) {
            log.warn("Record API log failed: method={} path={} status={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), auditException);
        }
    }

    private boolean isLogClearRequest(HttpServletRequest request) {
        if (!"DELETE".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return "/api/system/monitor/api-logs".equals(uri) || "/api/system/monitor/login-logs".equals(uri);
    }

    private Long currentLoginId() {
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            return loginId == null ? null : Long.valueOf(String.valueOf(loginId));
        }
        catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    private String limit(String value) {
        if (value == null || value.length() <= MAX_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_BODY_LENGTH);
    }

    /** 脱敏收敛到 SensitiveValueMasker 单点：凭据键 fail closed，内容键保持既有范围。 */
    private String maskSensitive(String value) {
        return SensitiveValueMasker.mask(value);
    }

    /**
     * 日志注入防护：请求行/URI/UA 等客户端可控值写入日志或审计前，
     * 将 CR/LF 及其他控制字符替换为可见转义，防止伪造日志边界。
     */
    static String sanitize(String value) {
        if (value == null || !CONTROL_CHARS.matcher(value).find()) {
            return value;
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7f) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
