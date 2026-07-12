package online.yudream.base.interfaces.platform.plugin.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthlibAliInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Authlib-Injector-API-Location";
    public static final String API_LOCATION = "/api/plugins/authlib-injector";

    private static final String AUTHLIB_PLUGIN_CODE = "authlib-injector";

    private final PluginAppService pluginAppService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isAuthlibProtocolRequest(request)
                && shouldExposeAli(request, response)
                && pluginAppService.enabled(AUTHLIB_PLUGIN_CODE)) {
            response.setHeader(HEADER, API_LOCATION);
        }
        return true;
    }

    /**
     * The authlib plugin writes its own protocol header. Writing it here as
     * well makes servlet containers expose a comma-joined, invalid URL.
     */
    private boolean isAuthlibProtocolRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return API_LOCATION.equals(requestUri) || requestUri.startsWith(API_LOCATION + "/");
    }

    private boolean shouldExposeAli(HttpServletRequest request, HttpServletResponse response) {
        return !response.containsHeader(HEADER)
                && ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod()));
    }
}
