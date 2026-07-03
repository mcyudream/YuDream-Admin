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

    public record SecurityPrincipal(Long userId, List<String> permissions) {

        public boolean superAdmin() {
            return permissions != null && permissions.contains("*");
        }
    }
}
