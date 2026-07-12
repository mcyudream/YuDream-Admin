package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.ai.PluginAiTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PluginAiToolRegistry {
    private final Map<String, List<PluginAiTool>> tools = new ConcurrentHashMap<>();

    public AutoCloseable register(String pluginCode, PluginAiTool tool) {
        tools.computeIfAbsent(pluginCode, ignored -> new ArrayList<>()).add(tool);
        return () -> tools.computeIfPresent(pluginCode, (ignored, values) -> {
            values.remove(tool);
            return values.isEmpty() ? null : values;
        });
    }

    public List<PluginAiTool> tools() {
        return tools.values().stream().flatMap(List::stream).toList();
    }
}
