package online.yudream.base.plugin.spi.system.messaging;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/** Raw invocation of the protocol selected by a messaging connection. */
public interface PluginMessagingRawService {
    CompletionStage<Map<String, Object>> invoke(String connectionId, String method, Map<String, Object> payload);
}
