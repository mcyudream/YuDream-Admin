package online.yudream.base.interfaces.platform.plugin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.common.ResultCode;
import online.yudream.base.interfaces.platform.plugin.request.PluginWsTicketRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginWsTicketRes;
import online.yudream.base.interfaces.platform.plugin.ws.PluginWsAssembler;
import online.yudream.base.interfaces.platform.plugin.ws.PluginWsPaths;
import online.yudream.base.interfaces.platform.plugin.ws.PluginWsTicketService;
import online.yudream.base.interfaces.platform.plugin.ws.WsAuthSupport;
import online.yudream.base.plugin.spi.system.security.PluginPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * WebSocket 握手票据发放端点（浏览器原生 WS 无法携带 Authorization 头）。
 * 仅对已认证主体（会话令牌/API Key/OAuth）发放；票据保留发放主体原始权限快照，
 * 握手时与实时权限取交集，不会把受限凭据升级为用户全量权限。
 * WS 桥接关闭时（yudream.plugin.ws.enabled=false）本端点同样停用。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.plugin.ws", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PluginWsTicketController {

    private final PluginWsTicketService ticketService;
    private final WsAuthSupport authSupport;

    @PostMapping(PluginWsPaths.TICKET_PATH)
    public Result<PluginWsTicketRes> issue(HttpServletRequest request,
                                           @Valid @RequestBody(required = false) PluginWsTicketRequest ticketRequest) {
        if (ticketRequest == null) {
            throw new BizException("请求体不能为空");
        }
        WsAuthSupport.CredentialSource[] source = {WsAuthSupport.CredentialSource.ANONYMOUS};
        PluginPrincipal principal = authSupport.fromRequestContext(request, s -> source[0] = s);
        if (principal == null || principal.userId() == null) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "WebSocket 票据仅对已认证用户发放");
        }
        return Result.ok(PluginWsAssembler.toTicketRes(
                ticketService.issue(principal.userId(), principal.permissions(),
                        ticketRequest.pluginCode(), ticketRequest.endpointPath())));
    }
}
