package online.yudream.base.interfaces.system.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.security.service.OAuthAuthAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.service.OAuthAuthenticationContext;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.common.ResultCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * OAuth 2.0 访问令牌的 HTTP 过滤器。
 * <p>
 * 仅接管形如 {@code Bearer ydo_at_xxx}（OAuth 颁发）的请求；与 {@link ApiKeyAuthenticationFilter}
 * 互补：API Key 走 {@code yda_} 前缀分支，二者前缀不同不会冲突。
 * 优先级在 API Key 过滤器之后，StpUtil / 业务过滤器之前。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
@RequiredArgsConstructor
public class OAuthAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String OAUTH_ACCESS_TOKEN_PREFIX = "ydo_at_";

    private final OAuthAuthAppService oauthAuthAppService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveAccessToken(request);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            OAuthAuthenticationContext.set(oauthAuthAppService.authenticate(token));
            filterChain.doFilter(request, response);
        } catch (BizException ex) {
            writeUnauthorized(response, ex.getMessage());
        } finally {
            OAuthAuthenticationContext.clear();
        }
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.startsWith(OAUTH_ACCESS_TOKEN_PREFIX) ? token : null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Result.fail(ResultCode.UNAUTHORIZED.getCode(), message));
    }
}