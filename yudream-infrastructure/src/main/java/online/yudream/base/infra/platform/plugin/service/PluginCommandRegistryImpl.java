package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.plugin.spi.system.command.PluginCommandDefinition;
import online.yudream.base.plugin.spi.system.command.PluginCommandHandler;
import online.yudream.base.plugin.spi.system.command.PluginCommandRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PluginCommandRegistryImpl implements PluginCommandRegistry, AutoCloseable {
    private final String pluginCode;
    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

    PluginCommandRegistryImpl(String pluginCode) {
        this.pluginCode = pluginCode;
    }

    @Override
    public AutoCloseable register(PluginCommandDefinition definition, PluginCommandHandler handler) {
        if (definition == null || definition.code() == null || definition.code().isBlank()
                || definition.command() == null || definition.command().isBlank() || handler == null) {
            throw new BizException("插件指令定义不完整");
        }
        Registration registration = new Registration(definition, handler);
        if (registrations.putIfAbsent(definition.code(), registration) != null) {
            throw new BizException("插件指令编码重复: " + pluginCode + ":" + definition.code());
        }
        return () -> registrations.remove(definition.code(), registration);
    }

    List<Registration> registrations() {
        return List.copyOf(new ArrayList<>(registrations.values()));
    }

    @Override
    public void close() {
        registrations.clear();
    }

    record Registration(PluginCommandDefinition definition, PluginCommandHandler handler) { }
}
