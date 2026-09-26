package online.yudream.base.interfaces.platform.plugin.ws;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 默认鉴权支撑：握手线程仍在 HTTP 过滤链之后（API Key/OAuth ThreadLocal 与
 * Sa-Token 会话均可用），票据通道的实时权限查询委托权限应用服务。
 * {@link #permissions(Long)} 返回 null 表示身份无法复核（未知/不可信），
 * 由握手层拒绝，票据残余有效期最长 60 秒。
 */
@Component
@RequiredArgsConstructor
public class DefaultWsAuthSupport implements WsAuthSupport {

    private final PermissionAppService permissionAppService;

    @Override
    public PluginPrincipal fromRequestContext(HttpServletRequest request, Consumer<CredentialSource> credentialSource) {
        try {
            if (SecurityPrincipalSupport.hasApiKeyAuthentication() || SecurityPrincipalSupport.hasOAuthAuthentication()) {
                SecurityPrincipalSupport.SecurityPrincipal principal = SecurityPrincipalSupport.current();
                credentialSource.accept(CredentialSource.AUTHORIZATION_CONTEXT);
                return new PluginPrincipal(principal.userId(), principal.permissions());
            }
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId != null) {
                Long userId = Long.valueOf(String.valueOf(loginId));
                credentialSource.accept(CredentialSource.AUTHORIZATION_CONTEXT);
                return new PluginPrincipal(userId, permissionAppService.getUserPermissions(userId));
            }
        } catch (RuntimeException ignored) {
            // 上下文不完整（如令牌失效）按匿名继续，由票据/权限判定兜底
        }
        credentialSource.accept(CredentialSource.ANONYMOUS);
        return null;
    }

    @Override
    public List<String> permissions(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return permissionAppService.getUserPermissions(userId);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
