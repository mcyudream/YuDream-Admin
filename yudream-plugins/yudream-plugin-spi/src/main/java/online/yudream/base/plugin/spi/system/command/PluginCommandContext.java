package online.yudream.base.plugin.spi.system.command;

import online.yudream.base.plugin.spi.system.messaging.PluginEvent;

import java.util.List;

public record PluginCommandContext(PluginEvent event, String command, List<String> arguments, Long userId) {
    public PluginCommandContext {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
    }
}
