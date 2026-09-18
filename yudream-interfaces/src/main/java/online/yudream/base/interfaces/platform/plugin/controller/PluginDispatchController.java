package online.yudream.base.interfaces.platform.plugin.controller;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
import online.yudream.base.application.system.user.service.PermissionAppService;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.plugin.assembler.PluginWebAssembler;
import online.yudream.base.interfaces.system.security.support.SecurityPrincipalSupport;
import online.yudream.base.plugin.spi.http.PluginSseStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Hidden
public class PluginDispatchController {

    private final PluginAppService pluginAppService;
    private final PermissionAppService permissionAppService;

    @RequestMapping({"/api/plugins/{code}", "/api/plugins/{code}/**"})
    public ResponseEntity<Object> dispatch(
            @PathVariable String code,
            @RequestBody(required = false) String body,
            HttpServletRequest request
    ) {
        SecurityPrincipalSupport.SecurityPrincipal principal = principal();
        PluginHttpDispatchDTO result = pluginAppService.dispatch(
                PluginWebAssembler.toDispatchCmd(code, pluginPath(code, request), body, request, principal)
        );
        return respond(result);
    }

    /** multipart/form-data 请求：parts 从 servlet 解析后透传给插件。 */
    @RequestMapping(value = {"/api/plugins/{code}", "/api/plugins/{code}/**"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> dispatchMultipart(
            @PathVariable String code,
            HttpServletRequest request
    ) {
        SecurityPrincipalSupport.SecurityPrincipal principal = principal();
        PluginHttpDispatchDTO result = pluginAppService.dispatch(
                PluginWebAssembler.toDispatchCmd(code, pluginPath(code, request), null, request, principal,
                        PluginWebAssembler.httpParts(request))
        );
        return respond(result);
    }

    private ResponseEntity<Object> respond(PluginHttpDispatchDTO result) {
        HttpHeaders headers = new HttpHeaders();
        result.getHeaders().forEach(headers::add);
        headers.setContentType(MediaType.parseMediaType(result.getContentType()));
        if (isSse(result)) {
            headers.setCacheControl("no-cache");
            return ResponseEntity.status(result.getStatus()).headers(headers).body(toEmitter((PluginSseStream) result.getBody()));
        }
        return ResponseEntity.status(result.getStatus()).headers(headers).body(responseBody(result));
    }

    private boolean isSse(PluginHttpDispatchDTO result) {
        return result.getContentType() != null
                && result.getContentType().toLowerCase().contains(MediaType.TEXT_EVENT_STREAM_VALUE)
                && result.getBody() instanceof PluginSseStream;
    }

    private SseEmitter toEmitter(PluginSseStream stream) {
        // 长连接场景（插件 SSE 事件流）需要足够长的超时；到期时正常 complete，
        // 避免 Spring 再抛 AsyncRequestTimeoutException 被全局异常处理器记成 ERROR。
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        PluginSseStream.Subscriber subscriber = new PluginSseStream.Subscriber() {
            @Override
            public void send(String event, Object data) {
                try {
                    SseEmitter.SseEventBuilder builder = SseEmitter.event();
                    if (event != null && !event.isBlank()) {
                        builder.name(event);
                    }
                    String eventId = extractEventId(data);
                    if (eventId != null) {
                        builder.id(eventId);
                    }
                    builder.data(data);
                    emitter.send(builder);
                } catch (Exception e) {
                    stream.unsubscribe(this);
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // emitter 可能已完成
                    }
                }
            }

            @Override
            public void complete() {
                stream.unsubscribe(this);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already completed
                }
            }

            @Override
            public void error(Throwable throwable) {
                stream.unsubscribe(this);
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // already completed
                }
            }
        };
        emitter.onCompletion(() -> stream.unsubscribe(subscriber));
        emitter.onTimeout(() -> {
            stream.unsubscribe(subscriber);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already completed
            }
        });
        emitter.onError(ignored -> stream.unsubscribe(subscriber));
        stream.subscribe(subscriber);
        return emitter;
    }

    /** 从事件信封提取 SSE id 字段，供客户端 Last-Event-ID 续传。 */
    @SuppressWarnings("unchecked")
    private static String extractEventId(Object data) {
        if (data instanceof Map<?, ?> map) {
            Object id = map.get("id");
            if (id != null) {
                return String.valueOf(id);
            }
        }
        return null;
    }

    private String pluginPath(String code, HttpServletRequest request) {
        String prefix = "/api/plugins/" + code;
        String uri = request.getRequestURI();
        if (!uri.startsWith(prefix)) {
            return "/";
        }
        String path = uri.substring(prefix.length());
        return path.isBlank() ? "/" : path;
    }

    private Object responseBody(PluginHttpDispatchDTO result) {
        if (!result.isWrapped()) {
            return result.getBody();
        }
        if (result.getContentType() != null && result.getContentType().toLowerCase().contains("application/json")) {
            return Result.ok(result.getBody());
        }
        return result.getBody();
    }

    private SecurityPrincipalSupport.SecurityPrincipal principal() {
        if (SecurityPrincipalSupport.hasApiKeyAuthentication() || SecurityPrincipalSupport.hasOAuthAuthentication()) {
            return SecurityPrincipalSupport.current();
        }
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return new SecurityPrincipalSupport.SecurityPrincipal(null, List.of());
        }
        Long userId = Long.valueOf(String.valueOf(loginId));
        return new SecurityPrincipalSupport.SecurityPrincipal(userId, permissionAppService.getUserPermissions(userId));
    }
}
