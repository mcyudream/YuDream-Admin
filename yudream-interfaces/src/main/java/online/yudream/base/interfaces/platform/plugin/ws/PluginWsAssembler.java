package online.yudream.base.interfaces.platform.plugin.ws;

import online.yudream.base.interfaces.platform.plugin.res.PluginWsTicketRes;

/**
 * 插件 WS 面 res 装配（Controller 禁止 new Res 的分层规则）。
 */
public final class PluginWsAssembler {

    private PluginWsAssembler() {
    }

    public static PluginWsTicketRes toTicketRes(PluginWsTicketService.IssuedTicket issued) {
        return new PluginWsTicketRes(issued.token(), issued.expiresIn());
    }
}
