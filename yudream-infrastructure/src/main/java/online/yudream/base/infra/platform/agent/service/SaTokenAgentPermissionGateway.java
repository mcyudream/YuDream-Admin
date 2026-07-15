package online.yudream.base.infra.platform.agent.service;

import cn.dev33.satoken.stp.StpUtil;
import online.yudream.base.domain.platform.agent.service.AgentPermissionGateway;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import online.yudream.base.infra.platform.plugin.service.PluginAiToolExecutionScope;

@Service
public class SaTokenAgentPermissionGateway implements AgentPermissionGateway {
    @Override
    public boolean hasPermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return true;
        }
        var pluginContext = PluginAiToolExecutionScope.current();
        if (pluginContext != null) {
            return pluginContext.permissions().contains(permissionCode);
        }
        if (ApiKeyAuthenticationContext.get() != null) {
            return ApiKeyAuthenticationContext.hasPermission(permissionCode);
        }
        return StpUtil.getLoginIdDefaultNull() != null && StpUtil.hasPermission(permissionCode);
    }
}
