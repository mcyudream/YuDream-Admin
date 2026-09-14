package online.yudream.base.domain.system.security.service;

import online.yudream.base.domain.system.security.valobj.OAuthAuthentication;

/**
 * 当前线程的 OAuth 访问令牌鉴权快照，由 OAuth 过滤器写入。
 * <p>
 * 同 {@link ApiKeyAuthenticationContext} 一样是 ThreadLocal，仅在请求线程内有效，
 * 用作 SecurityPrincipalSupport 解析当前用户的中间层。Filter 负责 set/clear。
 */
public class OAuthAuthenticationContext {

    private static final ThreadLocal<OAuthAuthentication> CURRENT = new ThreadLocal<>();

    private OAuthAuthenticationContext() {
    }

    public static void set(OAuthAuthentication authentication) {
        CURRENT.set(authentication);
    }

    public static OAuthAuthentication get() {
        return CURRENT.get();
    }

    public static boolean hasPermission(String permission) {
        OAuthAuthentication authentication = CURRENT.get();
        return authentication != null && authentication.hasPermission(permission);
    }

    public static void clear() {
        CURRENT.remove();
    }
}