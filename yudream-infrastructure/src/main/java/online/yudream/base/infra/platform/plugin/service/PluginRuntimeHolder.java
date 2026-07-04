package online.yudream.base.infra.platform.plugin.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import online.yudream.base.plugin.spi.core.PluginDescriptor;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;

import java.net.URLClassLoader;

@Getter
@Setter
@RequiredArgsConstructor
public class PluginRuntimeHolder {

    private final URLClassLoader classLoader;
    private final YuDreamPlugin plugin;
    private final PluginDescriptor descriptor;
    private final PluginContextImpl context;
    private boolean enabled;
}
