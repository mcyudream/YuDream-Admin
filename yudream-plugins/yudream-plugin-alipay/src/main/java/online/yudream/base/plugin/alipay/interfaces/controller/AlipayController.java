package online.yudream.base.plugin.alipay.interfaces.controller;

import online.yudream.base.plugin.alipay.bootstrap.AlipayPlugin;
import online.yudream.base.plugin.alipay.interfaces.http.AlipayHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;

public class AlipayController {

    private final AlipayHttpFacade http;

    public AlipayController(AlipayHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/config", permission = AlipayPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse config() {
        return http.config();
    }

    @PluginHttpEndpoint(method = "PUT", path = "/config", permission = AlipayPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse saveConfig(PluginHttpRequest request) {
        return http.saveConfig(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/recharges", permission = AlipayPlugin.USER_PERMISSION)
    public PluginHttpResponse createRecharge(PluginHttpRequest request) {
        return http.createRecharge(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/orders", permission = AlipayPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse orders(PluginHttpRequest request) {
        return http.orders(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/orders/{outTradeNo}", permission = AlipayPlugin.USER_PERMISSION)
    public PluginHttpResponse order(PluginHttpRequest request) {
        return http.order(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/notify", wrapResult = false)
    public PluginHttpResponse notify(PluginHttpRequest request) {
        return http.notify(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/notify/debug", permission = AlipayPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse notifyDebug(PluginHttpRequest request) {
        return http.notifyDebug(request);
    }
}
