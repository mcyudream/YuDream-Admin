package online.yudream.base.interfaces.platform.docs.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.docs.dto.ApiDocSettingsDTO;
import online.yudream.base.application.platform.docs.service.ApiDocAppService;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
public class ApiDocAccessFilter extends OncePerRequestFilter {

    private static final String API_DOC_VIEW_PERMISSION = "platform:docs:view";

    private final ApiDocAppService apiDocAppService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isApiDocRequest(request)) {
            ApiDocSettingsDTO settings = apiDocAppService.settings();
            if (!settings.isEnabled()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            if (!allowed(settings)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean allowed(ApiDocSettingsDTO settings) {
        if (settings.isApiKeyAccessEnabled()) {
            return SecurityPrincipalSupport.hasPermission(API_DOC_VIEW_PERMISSION);
        }
        return SecurityPrincipalSupport.hasLoginAuthentication()
                && SecurityPrincipalSupport.hasPermission(API_DOC_VIEW_PERMISSION);
    }

    private boolean isApiDocRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html");
    }
}
