package online.yudream.base.plugin.spi.system.ai;
import java.util.List;
public record PluginAiProviderOption(String code, String name, List<PluginAiModelOption> models) { public PluginAiProviderOption { models = models == null ? List.of() : List.copyOf(models); } }
