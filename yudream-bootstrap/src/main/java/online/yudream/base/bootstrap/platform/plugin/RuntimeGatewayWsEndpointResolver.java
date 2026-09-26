package online.yudream.base.bootstrap.platform.plugin;

import lombok.RequiredArgsConstructor;
import online.yudream.base.infra.platform.plugin.service.JarPluginRuntimeGateway;
import online.yudream.base.interfaces.platform.plugin.ws.PluginWsEndpointBinding;
import online.yudream.base.interfaces.platform.plugin.ws.WsEndpointResolver;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 插件 WebSocket 端点解析装配：委托插件运行时网关（要求插件已启用且路由仍注册）。
 * 放在 bootstrap 装配层，因为它同时依赖 interfaces 的桥接缝隙接口与 infrastructure
 * 的运行时网关（interfaces 不依赖 infrastructure，层级上不能放进去）。
 */
@Component
@RequiredArgsConstructor
public class RuntimeGatewayWsEndpointResolver implements WsEndpointResolver {

    private final JarPluginRuntimeGateway gateway;

    @Override
    public Optional<PluginWsEndpointBinding> resolve(String pluginCode, String path) {
        return gateway.resolveWsEndpoint(pluginCode, path)
                .map(resolution -> new PluginWsEndpointBinding(
                        resolution.pluginCode(),
                        resolution.registration().path(),
                        resolution.registration().permission(),
                        resolution.registration().handler()));
    }
}
