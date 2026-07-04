package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import online.yudream.base.plugin.spi.system.security.PluginSecurityService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PrincipalPluginSecurityService implements PluginSecurityService {

    @Override
    public boolean hasPermission(PluginPrincipal principal, String permission) {
        if (!StringUtils.hasText(permission)) {
            return true;
        }
        return principal != null && principal.hasPermission(permission);
    }

    @Override
    public void requirePermission(PluginPrincipal principal, String permission) {
        if (!hasPermission(principal, permission)) {
            throw new BizException("无此插件权限：" + permission);
        }
    }
}
