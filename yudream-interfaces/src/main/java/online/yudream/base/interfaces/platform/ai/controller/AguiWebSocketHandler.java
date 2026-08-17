package online.yudream.base.interfaces.platform.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.agent.service.BuiltinAgentCodes;
import online.yudream.base.application.platform.ai.cmd.CmsPageGenerateCmd;
import online.yudream.base.application.platform.ai.service.AiAppService;
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

    private final AiAppService aiAppService;
    private final ObjectMapper objectMapper;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if (session.getAttributes().putIfAbsent(STARTED_ATTRIBUTE, Boolean.TRUE) != null) {
            return;
        }
        String traceId = UUID.randomUUID().toString();
        try {
            SecurityPrincipal principal = SecurityPrincipalSupport.fromToken(token(session));
            CmsPageGenerateRequest request = objectMapper.readValue(message.getPayload(), CmsPageGenerateRequest.class);
            CmsPageGenerateCmd command = AiWebAssembler.toCmd(request, principal);
            run(session, command, request.getAgentCode(), traceId);
        }
        catch (Exception e) {
            log.debug("AG-UI WebSocket request rejected, traceId={}", traceId, e);
            send(session, AiWebAssembler.toAguiRunError(traceId, e.getMessage()));
            close(session);
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
            if (!session.isOpen()) {
                return;
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            }
        }
        catch (Exception e) {
            log.debug("AG-UI WebSocket send failed, type={}", data.getType(), e);
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
