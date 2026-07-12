package online.yudream.base.infra.platform.milky.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.netty.handler.codec.http.HttpHeaderNames;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;

/** Milky event transport; Milky's /event endpoint is consumed as WebSocket first. */
@Service
public class ReactorMilkyEventGateway {
    private final ObjectMapper mapper = new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public Disposable connect(MilkyConnection connection, Listener listener) {
        return HttpClient.create().headers(headers -> headers.set(HttpHeaderNames.AUTHORIZATION, "Bearer " + connection.getToken()))
                .websocket().uri(eventUri(connection.getBaseUrl(), connection.getToken())).handle((inbound, outbound) -> inbound.receive().asString()
                        .publishOn(Schedulers.boundedElastic()).doOnNext(raw -> receive(raw, listener)).then())
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1)).maxBackoff(Duration.ofMinutes(1)).jitter(0.25d)).subscribe();
    }

    private void receive(String raw, Listener listener) {
        try {
            listener.onEvent(mapper.readValue(raw, MilkyModels.Event.class), raw);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Milky 事件数据无效", exception);
        }
    }

    private URI eventUri(String baseUrl, String token) {
        URI base = URI.create(baseUrl);
        String scheme = "https".equalsIgnoreCase(base.getScheme()) ? "wss" : "ws";
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        return URI.create(scheme + "://" + base.getAuthority() + path + "/event?access_token="
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8));
    }

    public interface Listener { void onEvent(MilkyModels.Event event, String rawData); }
}
