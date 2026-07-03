package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;

public class ApiKeyAuthenticationContext {

    private static final ThreadLocal<ApiKeyAuthentication> CURRENT = new ThreadLocal<>();

    private ApiKeyAuthenticationContext() {
    }

    public static void set(ApiKeyAuthentication authentication) {
        CURRENT.set(authentication);
    }

    public static ApiKeyAuthentication get() {
        return CURRENT.get();
    }

    public static boolean hasPermission(String permission) {
        ApiKeyAuthentication authentication = CURRENT.get();
        return authentication != null && authentication.hasPermission(permission);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
