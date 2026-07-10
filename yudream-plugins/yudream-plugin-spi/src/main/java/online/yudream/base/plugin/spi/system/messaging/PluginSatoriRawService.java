package online.yudream.base.plugin.spi.system.messaging;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface PluginSatoriRawService {
    CompletionStage<Map<String, Object>> invoke(String connectionId, String method, Map<String, Object> payload);
}
