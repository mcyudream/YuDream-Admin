package online.yudream.base.plugin.spi.system.memory;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface PluginSemanticMemoryService {

    PluginSemanticMemoryStatus status();

    CompletionStage<Void> index(PluginSemanticMemoryRecord record);

    CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query);

    CompletionStage<Void> delete(String namespace, String id);
}
