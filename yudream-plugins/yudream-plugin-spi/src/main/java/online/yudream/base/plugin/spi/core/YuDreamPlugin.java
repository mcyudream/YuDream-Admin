package online.yudream.base.plugin.spi.core;

import online.yudream.base.plugin.spi.annotation.PluginSpec;

import java.util.List;

public interface YuDreamPlugin {

    default PluginDescriptor descriptor() {
        PluginSpec spec = getClass().getAnnotation(PluginSpec.class);
        if (spec == null) {
            throw new IllegalStateException("Plugin must override descriptor() or declare @PluginSpec");
        }
        return new PluginDescriptor(
                spec.code(),
                spec.name(),
                spec.version(),
                spec.description(),
                getClass().getName(),
                List.of(spec.dependencies()),
                List.of()
        );
    }

    default void onLoad(PluginContext context) {
    }

    default void onEnable(PluginContext context) {
    }

    default void onDisable(PluginContext context) {
    }

    default void onUnload(PluginContext context) {
    }
}
