package online.yudream.base.interfaces.platform.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.service.BuiltinAgentCodes;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.service.AiAppService;
import online.yudream.base.application.platform.ai.service.CmsCanvasClientBridge;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport.SecurityPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AG-UI 流式生成 WebSocket 端点，与 {@link AiController#streamCmsPage} 的 SSE 通道复用同一
 * 应用服务与 AG-UI 事件装配，仅在传输层由 SSE 换成 WebSocket。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AguiWebSocketHandler extends TextWebSocketHandler {

    private static final String STARTED_ATTRIBUTE = "agui.stream.started";
    private static final String BRIDGE_ATTRIBUTE = "agui.canvas.bridge";

    private final AiAppService aiAppService;
    private final ObjectMapper objectMapper;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // v2 agent 模式：运行期间的后续帧是浏览器回传的画布工具结果
        Object bridge = session.getAttributes().get(BRIDGE_ATTRIBUTE);
        if (bridge instanceof CmsCanvasClientBridge canvasBridge) {
            handleToolResultFrame(session, canvasBridge, message.getPayload());
            return;
        }
        if (session.getAttributes().putIfAbsent(STARTED_ATTRIBUTE, Boolean.TRUE) != null) {
            return;
        }
        String traceId = UUID.randomUUID().toString();
        try {
            SecurityPrincipal principal = SecurityPrincipalSupport.fromToken(token(session));
            CmsPageGenerateRequest request = objectMapper.readValue(message.getPayload(), CmsPageGenerateRequest.class);
            CmsPageGenerateCmd command = AiWebAssembler.toCmd(request, principal);
            if (isAgentMode(request)) {
                ensureAgentPermission(principal);
                runAgent(session, command, traceId);
                return;
            }
            run(session, command, request.getAgentCode(), traceId);
        }
        catch (Exception e) {
            log.debug("AG-UI WebSocket request rejected, traceId={}", traceId, e);
            send(session, AiWebAssembler.toAguiRunError(traceId, e.getMessage()));
            close(session);
        }
    }

    private boolean isAgentMode(CmsPageGenerateRequest request) {
        return "agent".equalsIgnoreCase(request.getMode() == null ? "" : request.getMode().trim())
                && !BuiltinAgentCodes.AGUI_CARD.equals(request.getAgentCode());
    }

    /** v2 直连生成网关不经 Agent 运行时权限闸门，这里对齐 SSE 端点的 platform:ai:generate 要求。 */
    private void ensureAgentPermission(SecurityPrincipal principal) {
        if (principal == null) {
            throw new BizException("未登录或令牌无效");
        }
        java.util.List<String> permissions = principal.permissions() == null ? java.util.List.of() : principal.permissions();
        if (!permissions.contains("*") && !permissions.contains("platform:ai:generate")) {
            throw new BizException("没有 AI 生成页面权限（platform:ai:generate）");
        }
    }

    private void handleToolResultFrame(WebSocketSession session, CmsCanvasClientBridge bridge, String payload) {
        try {
            var frame = objectMapper.readTree(payload);
            String type = frame.path("type").asText();
            if ("PING".equals(type)) {
                // 应用层心跳：浏览器 WebSocket 无法发原生 ping，此响应可防止反向代理在模型思考/工具等待期间误判空闲。
                sendRaw(session, java.util.Map.of("type", "PONG", "timestamp", System.currentTimeMillis()));
                return;
            }
            if (!"TOOL_RESULT".equals(type)) {
                return;
            }
            java.util.Map<String, Object> result = frame.hasNonNull("result")
                    ? objectMapper.convertValue(frame.get("result"), new com.fasterxml.jackson.core.type.TypeReference<>() {
                    })
                    : java.util.Map.of();
            bridge.complete(
                    frame.path("toolCallId").asText(""),
                    frame.path("ok").asBoolean(false),
                    result,
                    frame.path("error").asText(null));
        }
        catch (Exception e) {
            log.debug("AG-UI canvas tool result frame rejected", e);
        }
    }

    /**
     * v2 coding-agent 式运行：画布客户端工具经 {@link CmsCanvasClientBridge} 在浏览器真实执行，
     * 结果回流模型循环，直到模型自行完成。
     */
    private void runAgent(WebSocketSession session, CmsPageGenerateCmd command, String traceId) {
        CmsCanvasClientBridge bridge = new CmsCanvasClientBridge((toolCallId, toolName, args) ->
                send(session, AiWebAssembler.toAguiToolCallRequest(traceId, toolCallId, toolName, args)));
        session.getAttributes().put(BRIDGE_ATTRIBUTE, bridge);
        CompletableFuture.runAsync(() -> {
            AtomicBoolean activityStarted = new AtomicBoolean(false);
            try {
                send(session, AiWebAssembler.toAguiRunStarted(traceId));
                sendActivity(session, traceId, activityStarted, "accepted", "已收到请求，正在连接模型。");
                var result = aiAppService.streamCmsPageAgent(
                        command,
                        bridge,
                        delta -> send(session, AiWebAssembler.toAguiTextChunk(traceId, delta)),
                        reasoning -> send(session, AiWebAssembler.toAguiThinkingChunk(traceId, reasoning)),
                        tool -> {
                            Object id = tool.payload() == null ? null : tool.payload().get("_toolCallId");
                            String toolCallId = id == null ? traceId + "-tool" : String.valueOf(id);
                            send(session, AiWebAssembler.toAguiToolResult(traceId, toolCallId, tool));
                        },
                        progress -> sendActivity(session, traceId, activityStarted, progress.action(), progress.content())
                );
                send(session, AiWebAssembler.toAguiRunFinished(traceId, result));
            }
            catch (Exception e) {
                log.debug("AG-UI WebSocket agent stream failed, traceId={}", traceId, e);
                send(session, AiWebAssembler.toAguiRunError(traceId, e.getMessage()));
            }
            finally {
                session.getAttributes().remove(BRIDGE_ATTRIBUTE);
                bridge.failAll("运行已结束");
                close(session);
            }
        });
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        failBridge(session, "画布连接异常中断");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        failBridge(session, "画布连接已关闭");
    }

    private void failBridge(WebSocketSession session, String reason) {
        Object bridge = session.getAttributes().remove(BRIDGE_ATTRIBUTE);
        if (bridge instanceof CmsCanvasClientBridge canvasBridge) {
            canvasBridge.failAll(reason);
        }
    }

    private void run(WebSocketSession session, CmsPageGenerateCmd command, String agentCode, String traceId) {
        CompletableFuture.runAsync(() -> {
            AtomicInteger toolSequence = new AtomicInteger();
            AtomicBoolean activityStarted = new AtomicBoolean(false);
            try {
                send(session, AiWebAssembler.toAguiRunStarted(traceId));
                sendActivity(session, traceId, activityStarted, "accepted", "已收到请求，正在连接模型。");
                var result = aiAppService.streamCmsPage(
                        command,
                        delta -> send(session, AiWebAssembler.toAguiTextChunk(traceId, delta)),
                        reasoning -> send(session, AiWebAssembler.toAguiThinkingChunk(traceId, reasoning)),
                        tool -> {
                            String toolCallId = traceId + "-tool-" + toolSequence.incrementAndGet();
                            send(session, AiWebAssembler.toAguiToolStart(traceId, toolCallId, tool));
                            send(session, AiWebAssembler.toAguiToolResult(traceId, toolCallId, tool));
                        },
                        progress -> sendActivity(session, traceId, activityStarted, progress.action(), progress.content())
                );
                if (BuiltinAgentCodes.AGUI_CARD.equals(agentCode)) {
                    send(session, AiWebAssembler.toAguiCardSnapshot(traceId, result.getSummary()));
                }
                send(session, AiWebAssembler.toAguiRunFinished(traceId, result));
            }
            catch (Exception e) {
                log.debug("AG-UI WebSocket stream failed, traceId={}", traceId, e);
                send(session, AiWebAssembler.toAguiRunError(traceId, e.getMessage()));
            }
            finally {
                close(session);
            }
        });
    }

    private void sendActivity(WebSocketSession session, String traceId, AtomicBoolean activityStarted, String action, String content) {
        if (activityStarted.compareAndSet(false, true)) {
            send(session, AiWebAssembler.toAguiActivitySnapshot(traceId, action, content));
            return;
        }
        send(session, AiWebAssembler.toAguiActivityDelta(traceId, action, content));
    }

    private void send(WebSocketSession session, AguiStreamEventRes data) {
        try {
            sendRaw(session, data);
        }
        catch (Exception e) {
            log.debug("AG-UI WebSocket send failed, type={}", data.getType(), e);
        }
    }

    private void sendRaw(WebSocketSession session, Object data) {
        try {
            if (!session.isOpen()) {
                return;
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            }
        }
        catch (Exception e) {
            log.debug("AG-UI WebSocket raw send failed", e);
        }
    }

    private String token(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("token");
    }

    private void close(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        }
        catch (Exception ignored) {
        }
    }
}
