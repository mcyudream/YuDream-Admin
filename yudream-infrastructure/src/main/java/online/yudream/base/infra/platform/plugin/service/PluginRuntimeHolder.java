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
    private final String assetRevision;
    // 生命周期方法内在网关监视器下读写；enabled()/loaded() 等无锁读取依赖 volatile 保证可见性
    private volatile boolean enabled;
}
