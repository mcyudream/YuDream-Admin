package online.yudream.base.interfaces.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 缓存请求体，便于后续拦截器或日志读取。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentCachingRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean isCacheable = isCacheable(request);
        if (isCacheable) {
            ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request);
            filterChain.doFilter(wrapped, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean isCacheable(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return true;
        }
        String lower = contentType.toLowerCase();
        return lower.contains("application/json")
                || lower.contains("application/x-www-form-urlencoded")
                || lower.contains("text/");
    }
}
