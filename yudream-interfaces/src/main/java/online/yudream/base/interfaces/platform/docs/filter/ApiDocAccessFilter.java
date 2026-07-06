package online.yudream.base.interfaces.platform.docs.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.application.platform.docs.service.ApiDocAppService;
import online.yudream.base.interfaces.platform.docs.service.ApiDocAccessTicketService;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class ApiDocAccessFilter extends OncePerRequestFilter {

    private static final String API_DOC_VIEW_PERMISSION = "platform:docs:view";

    private final ApiDocAppService apiDocAppService;
    private final ApiDocAccessTicketService apiDocAccessTicketService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isApiDocRequest(request)) {
            ApiDocSettingsDTO settings = apiDocAppService.settings();
            if (!settings.isEnabled()) {
                writePlainStatus(response, HttpServletResponse.SC_NOT_FOUND, "API 文档未启用");
                return;
            }
            if (!allowed(request, settings)) {
                writePlainStatus(response, HttpServletResponse.SC_UNAUTHORIZED, "没有 API 文档访问权限");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean allowed(HttpServletRequest request, ApiDocSettingsDTO settings) {
        if (apiDocAccessTicketService.valid(resolveTicket(request))) {
            return true;
        }
        return settings.isApiKeyAccessEnabled()
                && SecurityPrincipalSupport.hasApiKeyPermission(API_DOC_VIEW_PERMISSION);
    }

    private boolean isApiDocRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html");
    }

    private String resolveTicket(HttpServletRequest request) {
        String parameter = request.getParameter(ApiDocAccessTicketService.PARAM_NAME);
        if (StringUtils.hasText(parameter)) {
            return parameter.trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return "";
        }
        for (Cookie cookie : cookies) {
            if (ApiDocAccessTicketService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return "";
    }

    private void writePlainStatus(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
