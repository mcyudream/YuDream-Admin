package online.yudream.base.interfaces.system.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.application.system.security.service.ApiKeyAuthAppService;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String KEY_PREFIX = "yda_";

    private final ApiKeyAuthAppService apiKeyAuthAppService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = resolveApiKey(request);
        if (!StringUtils.hasText(apiKey)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            ApiKeyAuthenticationContext.set(apiKeyAuthAppService.authenticate(apiKey));
            filterChain.doFilter(request, response);
        } catch (BizException ex) {
            writeUnauthorized(response, ex.getMessage());
        } finally {
            ApiKeyAuthenticationContext.clear();
        }
    }

    private String resolveApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (StringUtils.hasText(header)) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            return token.startsWith(KEY_PREFIX) ? token : null;
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.UNAUTHORIZED.getCode(), message));
    }
}
