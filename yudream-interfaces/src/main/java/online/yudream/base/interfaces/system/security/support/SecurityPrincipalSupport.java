package online.yudream.base.interfaces.system.security.support;

import cn.dev33.satoken.stp.StpUtil;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import online.yudream.base.domain.system.security.service.OAuthAuthenticationContext;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;
import online.yudream.base.domain.system.security.valobj.OAuthAuthentication;
import online.yudream.base.interfaces.common.ResultCode;

import java.util.List;

public class SecurityPrincipalSupport {

    private SecurityPrincipalSupport() {
    }

    public static SecurityPrincipal current() {
        ApiKeyAuthentication apiKeyAuthentication = ApiKeyAuthenticationContext.get();
        if (apiKeyAuthentication != null) {
            return new SecurityPrincipal(apiKeyAuthentication.userId(), apiKeyAuthentication.permissions());
        }
        OAuthAuthentication oauthAuthentication = OAuthAuthenticationContext.get();
        if (oauthAuthentication != null) {
            return new SecurityPrincipal(oauthAuthentication.userId(), oauthAuthentication.scopes());
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            throw unauthenticated();
        }
        return new SecurityPrincipal(Long.valueOf(String.valueOf(loginId)), StpUtil.getPermissionList());
    }

    /** 依据令牌值解析安全主体，用于 WebSocket 等无 HTTP 请求上下文的边界。 */
    public static SecurityPrincipal fromToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw unauthenticated();
        }
        if (tokenValue.startsWith("ydo_at_")) {
            // OAuth 访问令牌必须在请求线程内由 OAuthAuthenticationFilter 解析；
            // 静态工具仅消费已写入 ThreadLocal 的快照，避免反向依赖仓储。
            OAuthAuthentication oauthAuthentication = OAuthAuthenticationContext.get();
            if (oauthAuthentication == null) {
                throw new BizException("OAuth 令牌解析需经 HTTP 请求上下文");
            }
            return new SecurityPrincipal(oauthAuthentication.userId(), oauthAuthentication.scopes());
        }
        Object loginId = StpUtil.getLoginIdByToken(tokenValue);
        if (loginId == null) {
            throw unauthenticated();
        }
        return new SecurityPrincipal(Long.valueOf(String.valueOf(loginId)), StpUtil.getPermissionList(loginId));
    }

    private static BizException unauthenticated() {
        return new BizException(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMessage());
    }

    /**
     * 仅在当前请求线程由 OAuthAuthenticationFilter 设置了上下文时返回 OAuth 主体。
     * 不存在则回退到 {@link #current()} 的标准解析路径。
     */
    public static SecurityPrincipal currentOrOAuth() {
        OAuthAuthentication oauthAuthentication = OAuthAuthenticationContext.get();
        if (oauthAuthentication != null) {
            return new SecurityPrincipal(oauthAuthentication.userId(), oauthAuthentication.scopes());
        }
        return current();
    }

    public static boolean hasApiKeyAuthentication() {
        return ApiKeyAuthenticationContext.get() != null;
    }

    public static boolean hasLoginAuthentication() {
        return StpUtil.getLoginIdDefaultNull() != null;
    }

    public static boolean hasOAuthAuthentication() {
        return OAuthAuthenticationContext.get() != null;
    }

    public static boolean hasAnyAuthentication() {
        return hasApiKeyAuthentication() || hasLoginAuthentication() || hasOAuthAuthentication();
    }

    public static boolean hasPermission(String permission) {
        if (ApiKeyAuthenticationContext.hasPermission(permission)) {
            return true;
        }
        if (OAuthAuthenticationContext.hasPermission(permission)) {
            return true;
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId != null && containsPermission(StpUtil.getPermissionList(), permission);
    }

    public static boolean hasApiKeyPermission(String permission) {
        return ApiKeyAuthenticationContext.hasPermission(permission);
    }

    public static boolean hasOAuthPermission(String permission) {
        return OAuthAuthenticationContext.hasPermission(permission);
    }

    private static boolean containsPermission(List<String> permissions, String permission) {
        return permissions != null && (permissions.contains("*") || permissions.contains(permission));
    }

    public record SecurityPrincipal(Long userId, List<String> permissions) {

        public boolean superAdmin() {
            return permissions != null && permissions.contains("*");
        }
    }
}
