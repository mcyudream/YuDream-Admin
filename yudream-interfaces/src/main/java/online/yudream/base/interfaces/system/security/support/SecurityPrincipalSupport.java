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

    private static boolean containsPermission(List<String> permissions, String permission) {
        return permissions != null && (permissions.contains("*") || permissions.contains(permission));
    }

    public record SecurityPrincipal(Long userId, List<String> permissions) {

        public boolean superAdmin() {
            return permissions != null && permissions.contains("*");
        }
    }
}
