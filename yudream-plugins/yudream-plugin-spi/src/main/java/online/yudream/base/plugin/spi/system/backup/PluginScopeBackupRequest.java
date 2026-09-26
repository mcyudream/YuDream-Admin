package online.yudream.base.plugin.spi.system.backup;

import java.util.Map;

/**
 * 插件发起的范围备份请求：只允许触发本插件自己注册的 scopeCode。
 * targetCode 为空表示本机导出（在宿主备份中心可下载）；否则推送到该异地目标。
 * options 原样传给提供者的 export（如单实例过滤 {@code instanceId}）。
 *
 * @since 2.33.0
 */
public record PluginScopeBackupRequest(String scopeCode, String targetCode, Map<String, String> options) {
}
