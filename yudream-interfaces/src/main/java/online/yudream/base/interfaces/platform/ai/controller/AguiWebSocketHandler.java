package online.yudream.base.interfaces.platform.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.ai.service.AiBusinessRunAppService;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.platform.ai.assembler.AiRunWebAssembler;
import online.yudream.base.interfaces.platform.ai.assembler.AiWebAssembler;
import online.yudream.base.interfaces.platform.ai.request.CmsPageGenerateRequest;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** WebSocket 是统一持久运行的订阅与前端工具回执通道，断线只关闭订阅。 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AguiWebSocketHandler extends TextWebSocketHandler {
    private static final String USER = "agui.user";
    private static final String RUN = "agui.run";
    private static final String TOKEN = "agui.token";
    private final AiBusinessRunAppService businessRuns;
    private final AiRunAppService runs;
    private final ObjectMapper objectMapper;
    private final online.yudream.base.interfaces.platform.ai.service.AguiWsTicketService aguiWsTicketService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String traceId = UUID.randomUUID().toString();
        try {
            var frame = objectMapper.readTree(message.getPayload());
            if (session.getAttributes().get(USER) instanceof Long userId) {
                if ("PING".equals(frame.path("type").asText())) {
                    sendRaw(session, Map.of("type", "PONG", "timestamp", System.currentTimeMillis()));
                } else if ("TOOL_RESULT".equals(frame.path("type").asText())) {
                    var current = SecurityPrincipalSupport.fromToken((String) session.getAttributes().get(TOKEN));
                    if (current == null || !userId.equals(current.userId())) throw new BizException("登录状态已失效，请重新连接");
                    var permissions = current.permissions() == null ? List.<String>of() : current.permissions();
                    if (!permissions.contains("*") && !permissions.contains("platform:ai:generate")) throw new BizException("没有 AI 生成页面权限");
                    Map<String, Object> result = frame.hasNonNull("result")
                            ? objectMapper.convertValue(frame.get("result"), new com.fasterxml.jackson.core.type.TypeReference<>() { }) : Map.of();
                    runs.frontendResult(userId, String.valueOf(session.getAttributes().get(RUN)), frame.path("toolCallId").asText(),
                            frame.path("ok").asBoolean(), result, frame.path("error").asText(null), permissions);
                }
                return;
            }
            String loginToken = token(session);
            var principal = SecurityPrincipalSupport.fromToken(loginToken);
            if (principal == null || principal.userId() == null) throw new BizException("未登录或令牌无效");
            List<String> permissions = principal.permissions() == null ? List.of() : principal.permissions();
            if (!permissions.contains("*") && !permissions.contains("platform:ai:generate")) throw new BizException("没有 AI 生成页面权限");
            String runId;
            long after = Math.max(0, frame.path("after").asLong(0));
            if (frame.hasNonNull("resumeRunId")) {
                runId = frame.path("resumeRunId").asText();
                runs.get(principal.userId(), runId);
            } else {
                CmsPageGenerateRequest request = objectMapper.treeToValue(frame, CmsPageGenerateRequest.class);
                var command = AiWebAssembler.toCmd(request, principal);
                runId = businessRuns.cms(command).id();
                after = 0;
            }
            session.getAttributes().put(USER, principal.userId());
            session.getAttributes().put(RUN, runId);
            session.getAttributes().put(TOKEN, loginToken);
            subscribe(session, principal.userId(), runId, after);
        } catch (Exception error) {
            log.debug("AI WebSocket 请求失败，traceId={}", traceId, error);
            sendRaw(session, AiWebAssembler.toAguiRunError(traceId, error instanceof BizException ? error.getMessage() : "AI 请求失败，请稍后重试"));
            close(session);
        }
    }

    private void subscribe(WebSocketSession session, Long userId, String runId, long after) {
        Thread.startVirtualThread(() -> {
            long cursor = after;
            try {
                while (session.isOpen()) {
                    var events = runs.events(userId, runId, cursor);
                    for (var event : events) {
                        AguiStreamEventRes agui = AiRunWebAssembler.agui(event);
                        Map<String, Object> payload = objectMapper.convertValue(agui, new com.fasterxml.jackson.core.type.TypeReference<>() { });
                        payload.put("cursor", String.valueOf(event.cursor()));
                        sendRaw(session, payload);
                        cursor = event.cursor();
                    }
                    if (events.size() < 256 && runs.finished(userId, runId, cursor)) { close(session); return; }
                    if (events.size() < 256) Thread.sleep(350);
                }
            } catch (Exception error) { log.debug("AI WebSocket 订阅结束，runId={}", runId, error); close(session); }
        });
    }

    private void sendRaw(WebSocketSession session, Object data) {
        try {
            synchronized (session) { if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data))); }
        } catch (Exception error) { log.debug("AI WebSocket 传输已关闭", error); close(session); }
    }
    private String token(WebSocketSession session) {
        if (session.getUri() == null) return null;
        var params = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        String value = aguiWsTicketService.consume(params.getFirst("ticket"));
        return value == null ? params.getFirst("token") : value;
    }
    private void close(WebSocketSession session) {
        try { if (session.isOpen()) session.close(CloseStatus.NORMAL); } catch (Exception ignored) { }
    }
}
