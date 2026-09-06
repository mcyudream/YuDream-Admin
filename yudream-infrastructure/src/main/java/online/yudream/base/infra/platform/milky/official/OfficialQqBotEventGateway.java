package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.HttpHeaderNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.infra.platform.milky.service.ReactorMilkyEventGateway;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 官方机器人 Gateway：Hello/Identify/Heartbeat/Resume，事件归一后交给宿主总线。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialQqBotEventGateway {
    private static final int OP_DISPATCH = 0;
    private static final int OP_HEARTBEAT = 1;
    private static final int OP_IDENTIFY = 2;
    private static final int OP_RESUME = 6;
    private static final int OP_RECONNECT = 7;
    private static final int OP_INVALID_SESSION = 9;
    private static final int OP_HELLO = 10;
    private static final int OP_HEARTBEAT_ACK = 11;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final OfficialQqBotAccessTokenClient tokens;
    private final OfficialQqBotSessionStore sessions;
    private final OfficialQqBotApiAdapter apiAdapter;
    private final ObjectMapper mapper = new ObjectMapper();

    public Disposable connect(MilkyConnection connection, ReactorMilkyEventGateway.Listener listener) {
        Long connectionId = connection.getId();
        AtomicReference<Session> session = new AtomicReference<>();
        AtomicBoolean resumable = new AtomicBoolean();
        return Mono.defer(() -> connectOnce(connection, listener, session, resumable))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofMinutes(1)).jitter(0.25d)
                        .doBeforeRetry(signal -> log.warn("Official QQ bot gateway retrying: connectionId={}, attempt={}",
                                connectionId, signal.totalRetries() + 1, signal.failure())))
                .doFinally(signal -> {
                    sessions.forget(connectionId);
                    log.info("Official QQ bot gateway closed: connectionId={}, signal={}", connectionId, signal);
                })
                .subscribe();
    }

    private Mono<Void> connectOnce(MilkyConnection connection, ReactorMilkyEventGateway.Listener listener,
                                   AtomicReference<Session> session, AtomicBoolean resumable) {
        Long connectionId = connection.getId();
        URI gateway = gatewayUrl(connection);
        log.info("Official QQ bot gateway connecting: connectionId={}, endpoint={}", connectionId, gateway);
        AtomicLong heartbeatInterval = new AtomicLong(45_000);
        AtomicReference<Disposable> heartbeat = new AtomicReference<>();
        return HttpClient.create()
                .headers(headers -> {
                    headers.set(HttpHeaderNames.AUTHORIZATION, tokens.authorization(connection.toApiContext()));
                    headers.set("X-Union-Appid", connection.getAppId());
                })
                .websocket()
                .uri(gateway)
                .handle((inbound, outbound) -> inbound.receive().asString()
                        .publishOn(Schedulers.boundedElastic())
                        .concatMap(raw -> handle(connection, raw, outbound, listener, session, resumable, heartbeatInterval, heartbeat))
                        .then())
                .doOnError(error -> log.error("Official QQ bot gateway transport failed: connectionId={}", connectionId, error))
                .doFinally(signal -> {
                    Disposable beat = heartbeat.getAndSet(null);
                    if (beat != null) {
                        beat.dispose();
                    }
                })
                .then();
    }

    private Mono<Void> handle(MilkyConnection connection, String raw, WebsocketOutbound outbound,
                              ReactorMilkyEventGateway.Listener listener, AtomicReference<Session> session,
                              AtomicBoolean resumable, AtomicLong heartbeatInterval, AtomicReference<Disposable> heartbeat) {
        JsonNode payload;
        try {
            payload = mapper.readTree(raw);
        } catch (Exception exception) {
            log.warn("Ignoring invalid official gateway payload: connectionId={}", connection.getId(), exception);
            return Mono.empty();
        }
        int op = payload.path("op").asInt(-1);
        JsonNode sequenceNode = payload.get("s");
        if (sequenceNode != null && !sequenceNode.isNull()) {
            Session current = session.get();
            if (current != null) {
                current.lastSequence().set(sequenceNode.asLong());
            }
        }
        return switch (op) {
            case OP_HELLO -> hello(connection, payload, outbound, session, resumable, heartbeatInterval, heartbeat);
            case OP_HEARTBEAT_ACK -> Mono.empty();
            case OP_RECONNECT -> {
                resumable.set(true);
                yield Mono.error(new IllegalStateException("official gateway requested reconnect"));
            }
            case OP_INVALID_SESSION -> {
                resumable.set(false);
                session.set(null);
                yield Mono.error(new IllegalStateException("official gateway session invalid"));
            }
            case OP_DISPATCH -> {
                dispatch(connection, payload, listener, session);
                yield Mono.empty();
            }
            default -> Mono.empty();
        };
    }

    private Mono<Void> hello(MilkyConnection connection, JsonNode payload, WebsocketOutbound outbound,
                             AtomicReference<Session> session, AtomicBoolean resumable,
                             AtomicLong heartbeatInterval, AtomicReference<Disposable> heartbeat) {
        long interval = payload.path("d").path("heartbeat_interval").asLong(45_000);
        heartbeatInterval.set(Math.max(5_000, interval));
        startHeartbeat(outbound, session, heartbeatInterval, heartbeat);
        Session current = session.get();
        if (resumable.get() && current != null && current.sessionId() != null) {
            return send(outbound, op(OP_RESUME, Map.of(
                    "token", tokens.token(connection.toApiContext()),
                    "session_id", current.sessionId(),
                    "seq", current.lastSequence().get())));
        }
        return send(outbound, op(OP_IDENTIFY, Map.of(
                "token", tokens.token(connection.toApiContext()),
                "intents", connection.officialIntents(),
                "shard", new int[]{0, 1})));
    }

    private void dispatch(MilkyConnection connection, JsonNode payload, ReactorMilkyEventGateway.Listener listener,
                          AtomicReference<Session> session) {
        String type = payload.path("t").asText("");
        if ("READY".equals(type)) {
            String sessionId = payload.path("d").path("session_id").asText(null);
            session.set(new Session(sessionId, new AtomicLong(payload.path("s").asLong(0))));
            String userId = payload.path("d").path("user").path("id").asText(connection.getAppId());
            sessions.rememberSelf(connection.getId(), userId);
        }
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(payload, sessions, connection.getId());
        if (event == null) {
            return;
        }
        try {
            listener.onEvent(event, raw(payload));
        } catch (Exception exception) {
            log.error("Official QQ bot event listener failed: connectionId={}, eventType={}",
                    connection.getId(), event.eventType(), exception);
        }
    }

    private void startHeartbeat(WebsocketOutbound outbound, AtomicReference<Session> session,
                                AtomicLong heartbeatInterval, AtomicReference<Disposable> heartbeat) {
        Disposable previous = heartbeat.getAndSet(null);
        if (previous != null) {
            previous.dispose();
        }
        heartbeat.set(Mono.delay(Duration.ofMillis(heartbeatInterval.get()))
                .repeat()
                .concatMap(ignored -> {
                    Session current = session.get();
                    Long seq = current == null ? null : current.lastSequence().get();
                    return send(outbound, op(OP_HEARTBEAT, seq == null || seq == 0 ? null : seq));
                })
                .subscribe());
    }

    private Mono<Void> send(WebsocketOutbound outbound, Object payload) {
        try {
            return outbound.sendString(Mono.just(mapper.writeValueAsString(payload))).then();
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    private ObjectNode op(int op, Object data) {
        ObjectNode node = mapper.createObjectNode();
        node.put("op", op);
        if (data == null) {
            node.putNull("d");
        } else {
            node.set("d", mapper.valueToTree(data));
        }
        return node;
    }

    private URI gatewayUrl(MilkyConnection connection) {
        Object result = apiAdapter.invoke(connection.toApiContext(), "GET /gateway", Map.of());
        String url = null;
        if (result instanceof Map<?, ?> map && map.get("url") != null) {
            url = String.valueOf(map.get("url"));
        }
        if (url == null || url.isBlank()) {
            url = "wss://api.sgroup.qq.com/websocket";
        }
        return URI.create(url);
    }

    private String raw(JsonNode payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (Exception exception) {
            return payload.toString();
        }
    }

    private record Session(String sessionId, AtomicLong lastSequence) { }
}
