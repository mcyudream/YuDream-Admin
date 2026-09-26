package online.yudream.base.interfaces.platform.plugin.ws;

import java.util.Optional;

/**
 * 握手/连接阶段的端点解析缝隙：要求插件已启用且路由仍注册。
 * 宿主实现委托运行时网关；测试用 Lambda 替身。
 */
public interface WsEndpointResolver {

    Optional<PluginWsEndpointBinding> resolve(String pluginCode, String path);
}
