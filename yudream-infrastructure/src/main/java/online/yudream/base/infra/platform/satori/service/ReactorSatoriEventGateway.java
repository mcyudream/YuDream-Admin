package online.yudream.base.infra.platform.satori.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.enumerate.SatoriOpcode;
import online.yudream.base.domain.platform.satori.model.SatoriModels;
import online.yudream.base.infra.platform.satori.json.SatoriJsonMapper;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

/** Reactor Netty implementation of the Satori WebSocket wire protocol. */
@Service
public class ReactorSatoriEventGateway {
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(10);
    private final ObjectMapper mapper = SatoriJsonMapper.createObjectMapper();

    public Disposable connect(SatoriConnection connection, String lastSequence, SatoriSessionListener listener) {
        return HttpClient.create()
                .headers(headers -> headers.set(HttpHeaderNames.AUTHORIZATION, "Bearer " + connection.getToken()))
                .websocket()
                .uri(websocketUri(connection.getBaseUrl()).toString())
                .handle((inbound, outbound) -> {
                    Sinks.Many<String> controls = Sinks.many().unicast().onBackpressureBuffer();
                    Flux<String> messages = Flux.concat(
                            Mono.just(identify(connection.getToken(), lastSequence)),
                            Flux.merge(controls.asFlux(), Flux.interval(HEARTBEAT_INTERVAL).map(ignored -> envelope(SatoriOpcode.PING, null)))
                    );
                    Mono<Void> send = outbound.sendString(messages).then();
                    Mono<Void> receive = inbound.receive().asString()
                            .publishOn(Schedulers.boundedElastic())
                            .doOnNext(raw -> receive(raw, listener, controls))
                            .then();
                    return Mono.when(send, receive);
                })
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofMinutes(1)).jitter(0.25d))
                .subscribe();
    }

    private void receive(String raw, SatoriSessionListener listener, Sinks.Many<String> controls) {
        try {
            JsonNode envelope = mapper.readTree(raw);
            SatoriOpcode opcode = SatoriOpcode.fromValue(envelope.path("op").asInt(-1));
            JsonNode body = envelope.path("body");
            switch (opcode) {
                case PING -> controls.tryEmitNext(envelope(SatoriOpcode.PONG, null));
                case READY -> listener.onReady(readLogins(body.path("logins")), mapper.treeToValue(body, SatoriModels.SatoriMeta.class), proxyUrls(body));
                case META -> listener.onMeta(mapper.treeToValue(body, SatoriModels.SatoriMeta.class), proxyUrls(body));
                case EVENT -> listener.onEvent(mapper.treeToValue(body, SatoriModels.SatoriEvent.class), mapper.writeValueAsString(body));
                default -> { }
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Satori WebSocket 帧无效", exception);
        }
    }

    private List<SatoriModels.SatoriLogin> readLogins(JsonNode node) throws JsonProcessingException {
        if (!node.isArray()) return List.of();
        return mapper.readerForListOf(SatoriModels.SatoriLogin.class).readValue(node);
    }

    private Set<String> proxyUrls(JsonNode body) {
        JsonNode urls = body.path("proxy_urls");
        if (!urls.isArray()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        urls.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText()); });
        return Set.copyOf(result);
    }

    private String identify(String token, String lastSequence) {
        try {
            var body = mapper.createObjectNode().put("token", token);
            if (lastSequence != null && !lastSequence.isBlank()) body.put("sn", lastSequence);
            return envelope(SatoriOpcode.IDENTIFY, body);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private String envelope(SatoriOpcode opcode, Object body) {
        try {
            var root = mapper.createObjectNode().put("op", opcode.value());
            if (body != null) root.set("body", body instanceof JsonNode node ? node : mapper.valueToTree(body));
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Satori 帧编码失败", exception);
        }
    }

    private URI websocketUri(String baseUrl) {
        URI base = URI.create(baseUrl);
        String scheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        return URI.create(scheme + "://" + base.getAuthority() + path + "/v1/events");
    }

    public interface SatoriSessionListener {
        void onReady(List<SatoriModels.SatoriLogin> logins, SatoriModels.SatoriMeta meta, Set<String> proxyUrls);
        void onMeta(SatoriModels.SatoriMeta meta, Set<String> proxyUrls);
        void onEvent(SatoriModels.SatoriEvent event, String rawData);
    }
}
