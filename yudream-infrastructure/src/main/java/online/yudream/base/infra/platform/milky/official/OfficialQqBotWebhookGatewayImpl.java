package online.yudream.base.infra.platform.milky.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.milky.port.OfficialQqBotWebhookGateway;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.event.MilkyEventPublished;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfficialQqBotWebhookGatewayImpl implements OfficialQqBotWebhookGateway {
    private final OfficialQqBotSessionStore sessions;
    private final ApplicationEventPublisher publisher;
    private final ObjectMapper mapper;

    @Override
    public Object handle(MilkyConnection connection, String rawBody, String signature, String timestamp) {
        JsonNode payload;
        try {
            payload = mapper.readTree(rawBody == null ? "{}" : rawBody);
        } catch (Exception exception) {
            BizException failure = new BizException("官方机器人 Webhook 载荷无法解析");
            failure.initCause(exception);
            throw failure;
        }
        int op = payload.path("op").asInt(-1);
        if (op == 13) {
            return validation(connection, payload);
        }
        if (!OfficialQqBotEd25519.verify(connection.getAppSecret(), timestamp, rawBody == null ? "" : rawBody, signature)) {
            throw new BizException("官方机器人 Webhook 签名无效");
        }
        MilkyModels.Event event = OfficialQqBotEventNormalizer.normalize(payload, sessions, connection.getId());
        if (event != null) {
            publisher.publishEvent(new MilkyEventPublished(connection.getId(), event));
        }
        return Map.of("op", 12);
    }

    private Map<String, Object> validation(MilkyConnection connection, JsonNode payload) {
        String plainToken = payload.path("d").path("plain_token").asText("");
        String eventTs = payload.path("d").path("event_ts").asText("");
        if (plainToken.isBlank()) {
            throw new BizException("官方机器人回调校验缺少 plain_token");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("plain_token", plainToken);
        result.put("signature", OfficialQqBotEd25519.signValidation(connection.getAppSecret(), eventTs, plainToken));
        return result;
    }
}
