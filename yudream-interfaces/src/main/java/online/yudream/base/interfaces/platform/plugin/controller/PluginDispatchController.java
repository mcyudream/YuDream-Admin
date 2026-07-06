package online.yudream.base.interfaces.platform.plugin.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.plugin.dto.PluginHttpDispatchDTO;
import online.yudream.base.application.platform.plugin.service.PluginAppService;
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

@RestController
@RequiredArgsConstructor
public class PluginDispatchController {

    private final PluginAppService pluginAppService;

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
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        PluginSseStream.Subscriber subscriber = new PluginSseStream.Subscriber() {
            @Override
            public void send(String event, Object data) {
                try {
                    emitter.send(SseEmitter.event().name(event).data(data));
                } catch (Exception e) {
                    stream.unsubscribe(this);
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void complete() {
                stream.unsubscribe(this);
                emitter.complete();
            }

            @Override
            public void error(Throwable throwable) {
                stream.unsubscribe(this);
                emitter.completeWithError(throwable);
            }
        };
        emitter.onCompletion(() -> stream.unsubscribe(subscriber));
        emitter.onTimeout(() -> stream.unsubscribe(subscriber));
        emitter.onError(ignored -> stream.unsubscribe(subscriber));
        stream.subscribe(subscriber);
        return emitter;
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
        if (!SecurityPrincipalSupport.hasAnyAuthentication()) {
            return new SecurityPrincipalSupport.SecurityPrincipal(null, List.of());
        }
        return SecurityPrincipalSupport.current();
    }
}
