package online.yudream.base.interfaces.system.security.support;

import cn.dev33.satoken.stp.StpUtil;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import online.yudream.base.domain.system.security.valobj.ApiKeyAuthentication;

import java.util.List;

public class SecurityPrincipalSupport {

    private SecurityPrincipalSupport() {
    }

    public static SecurityPrincipal current() {
        ApiKeyAuthentication apiKeyAuthentication = ApiKeyAuthenticationContext.get();
        if (apiKeyAuthentication != null) {
            return new SecurityPrincipal(apiKeyAuthentication.userId(), apiKeyAuthentication.permissions());
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            throw new BizException("当前用户未登录");
        }
        return new SecurityPrincipal(Long.valueOf(String.valueOf(loginId)), StpUtil.getPermissionList());
    }

    /** 依据令牌值解析安全主体，用于 WebSocket 等无 HTTP 请求上下文的边界。 */
    public static SecurityPrincipal fromToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new BizException("当前用户未登录");
        }
        Object loginId = StpUtil.getLoginIdByToken(tokenValue);
        if (loginId == null) {
            throw new BizException("当前用户未登录");
        }
        return new SecurityPrincipal(Long.valueOf(String.valueOf(loginId)), StpUtil.getPermissionList(loginId));
    }

    public static boolean hasApiKeyAuthentication() {
        return ApiKeyAuthenticationContext.get() != null;
    }

    public static boolean hasLoginAuthentication() {
        return StpUtil.getLoginIdDefaultNull() != null;
    }

    public static boolean hasAnyAuthentication() {
        return hasApiKeyAuthentication() || hasLoginAuthentication();
    }

    public static boolean hasPermission(String permission) {
        if (ApiKeyAuthenticationContext.hasPermission(permission)) {
            return true;
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        return loginId != null && containsPermission(StpUtil.getPermissionList(), permission);
    }

    public static boolean hasApiKeyPermission(String permission) {
        return ApiKeyAuthenticationContext.hasPermission(permission);
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
