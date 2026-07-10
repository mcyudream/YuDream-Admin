package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.satori.service.MessageDeliveryAppService;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.message.SatoriMessageContent;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.InternalRequest;
import online.yudream.base.domain.platform.satori.model.SatoriApiModels.SatoriApiContext;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.service.SatoriInternalGateway;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageContent;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageRequest;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageResult;
import online.yudream.base.plugin.spi.system.messaging.PluginMessagingService;
import online.yudream.base.plugin.spi.system.messaging.PluginSatoriRawService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Stable SPI boundary. Plugin code only receives SPI records and CompletionStage. */
@Service
@RequiredArgsConstructor
public class PluginMessagingFrameworkService implements PluginMessagingService, PluginSatoriRawService {
    private final MessageDeliveryAppService messageDeliveryAppService;
    private final SatoriConnectionRepo connectionRepo;
    private final SatoriInternalGateway internalGateway;

    @Override
    public CompletionStage<PluginMessageResult> send(PluginMessageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (request == null || request.content() == null) throw new BizException("插件消息请求不能为空");
            MessageDeliveryAppService.DeliveryResult result = messageDeliveryAppService.deliver(
                    new MessageDeliveryAppService.DeliveryRequest(parseConnectionId(request.connectionId()), request.platform(),
                            request.userId(), request.channelId(), toContent(request.content())));
            return new PluginMessageResult(result.messages().stream().map(message -> message.id()).toList(),
                    result.rendered(), result.degraded());
        });
    }

    @Override
    public CompletionStage<Map<String, Object>> invoke(String connectionId, String method, Map<String, Object> payload) {
        return CompletableFuture.supplyAsync(() -> {
            SatoriConnection connection = connectionRepo.findById(parseConnectionId(connectionId))
                    .orElseThrow(() -> new BizException("Satori 连接不存在"));
            if (!connection.enabled()) throw new BizException("Satori 连接未启用");
            if (method == null || method.isBlank()) throw new BizException("Satori 原生方法不能为空");
            Object response = internalGateway.invoke(new SatoriApiContext(connection.getBaseUrl(), connection.getToken(),
                    required(payload, "platform"), required(payload, "userId")),
                    new InternalRequest(method.trim(), payload == null ? Map.of() : Map.copyOf(payload)));
            if (response instanceof Map<?, ?> map) {
                Map<String, Object> copied = new LinkedHashMap<>();
                map.forEach((key, value) -> copied.put(String.valueOf(key), value));
                return Map.copyOf(copied);
            }
            return Map.of("data", response);
        });
    }

    private SatoriMessageContent toContent(PluginMessageContent source) {
        return new SatoriMessageContent(SatoriMessageContent.Type.valueOf(source.type().name()), source.content(),
                source.attachments().stream().map(item -> new SatoriMessageContent.Attachment(item.url(), item.title(), item.contentType())).toList(),
                source.referrer());
    }

    private Long parseConnectionId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException exception) {
            throw new BizException("Satori 连接 ID 无效");
        }
    }

    private String required(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new BizException("Satori 原生调用缺少 " + key);
        return String.valueOf(value).trim();
    }
}
