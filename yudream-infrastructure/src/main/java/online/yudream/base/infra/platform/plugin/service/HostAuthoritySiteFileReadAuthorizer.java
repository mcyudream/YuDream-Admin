package online.yudream.base.infra.platform.plugin.service;

import cn.dev33.satoken.stp.StpUtil;
import online.yudream.base.domain.system.security.service.ApiKeyAuthenticationContext;
import org.springframework.stereotype.Component;

/**
 * 站点目录跨插件读取的默认授权实现：现有宿主规则为「持有插件管理查看权限
 * {@code platform:plugin:view}」；身份只取当前会话（Sa-Token 登录态、API Key 上下文或
 * 插件 AI 工具执行作用域），任何环节缺失按未认证处理 fail-closed。
 * <p>
 * 不使用 {@code plugin:{ownerPluginCode}:view} 一类不存在的按插件实例权限码；
 * 后续若引入独立的站点文件授权规则，只需替换本实现。
 */
@Component
public class HostAuthoritySiteFileReadAuthorizer implements SiteFileReadAuthorizer {

    /** 宿主现行权限码：查看插件管理（{@code PluginController} 注册）。 */
    public static final String PERMISSION = "platform:plugin:view";

    @Override
    public boolean canReadOtherPluginFiles() {
        var aiContext = PluginAiToolExecutionScope.current();
        if (aiContext != null) {
            return aiContext.userId() != null && aiContext.hasPermission(PERMISSION);
        }
        var apiKey = ApiKeyAuthenticationContext.get();
        if (apiKey != null) {
            return apiKey.userId() != null && apiKey.hasPermission(PERMISSION);
        }
        try {
            return StpUtil.getLoginIdDefaultNull() != null && StpUtil.hasPermission(PERMISSION);
        }
        catch (RuntimeException exception) {
            // 无 Web 上下文等场景按未认证处理，fail-closed
            return false;
        }
    }
}
