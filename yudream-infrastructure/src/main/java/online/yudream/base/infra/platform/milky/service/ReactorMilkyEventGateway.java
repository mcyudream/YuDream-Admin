package online.yudream.base.infra.platform.milky.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.netty.handler.codec.http.HttpHeaderNames;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

/** Milky event transport; Milky's /event endpoint is consumed as WebSocket first. */
@Service
@Slf4j
public class ReactorMilkyEventGateway {
    private final ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public Disposable connect(MilkyConnection connection, Listener listener) {
        Long connectionId = connection.getId();
        String endpoint = safeEndpoint(connection.getBaseUrl());
        return HttpClient.create().headers(headers -> headers.set(HttpHeaderNames.AUTHORIZATION, "Bearer " + connection.getToken()))
                .websocket().uri(eventUri(connection.getBaseUrl(), connection.getToken())).handle((inbound, outbound) -> inbound.receive().asString()
                        .publishOn(Schedulers.boundedElastic()).doOnNext(raw -> receive(connectionId, raw, listener)).then())
                .doOnSubscribe(ignored -> log.info("Milky WebSocket connecting: connectionId={}, endpoint={}", connectionId, endpoint))
                .doOnError(error -> log.error("Milky WebSocket transport failed: connectionId={}, endpoint={}", connectionId, endpoint, error))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofMinutes(1)).jitter(0.25d)
                        .doBeforeRetry(signal -> log.warn("Milky WebSocket retrying: connectionId={}, endpoint={}, attempt={}",
                                connectionId, endpoint, signal.totalRetries() + 1, signal.failure())))
                .doFinally(signal -> log.info("Milky WebSocket closed: connectionId={}, endpoint={}, signal={}", connectionId, endpoint, signal))
                .subscribe();
    }

    private void receive(Long connectionId, String raw, Listener listener) {
        MilkyModels.Event event;
        try {
            event = mapper.readValue(raw, MilkyModels.Event.class);
        } catch (Exception exception) {
            log.warn("Ignoring invalid Milky event: connectionId={}", connectionId, exception);
            return;
        }
        try {
            listener.onEvent(event, raw);
        } catch (Exception exception) {
            log.error("Milky event listener failed: connectionId={}, eventType={}", connectionId, event.eventType(), exception);
        }
    }

    private URI eventUri(String baseUrl, String token) {
        URI base = URI.create(baseUrl);
        String scheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        return URI.create(scheme + "://" + base.getAuthority() + path + "/event?access_token="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8));
    }

    private String safeEndpoint(String baseUrl) {
        try {
            URI base = URI.create(baseUrl);
            String port = base.getPort() < 0 ? "" : ":" + base.getPort();
            String path = base.getPath() == null ? "" : base.getPath();
            return base.getScheme() + "://" + base.getHost() + port + path;
        } catch (IllegalArgumentException exception) {
            return "<invalid>";
        }
    }

    public interface Listener { void onEvent(MilkyModels.Event event, String rawData); }
}
