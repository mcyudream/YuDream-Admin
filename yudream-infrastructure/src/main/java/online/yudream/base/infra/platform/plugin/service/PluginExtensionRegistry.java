package online.yudream.base.infra.platform.plugin.service;

import online.yudream.base.plugin.spi.system.extension.PluginExtensionQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 插件扩展注册表：按扩展点接口聚合各插件注册的扩展实现。
 * 注册句柄随插件 disable/unload 由 PluginContextImpl 统一回收，
 * 因此查询结果只包含当前已启用插件的贡献。
 */
@Service
public class PluginExtensionRegistry implements PluginExtensionQuery {

    private final ConcurrentMap<Class<?>, List<Registration>> registrations = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public <I> AutoCloseable register(String pluginCode, Class<I> extensionPoint, I extension, int priority) {
        Registration registration = new Registration(pluginCode, priority, sequence.getAndIncrement(), extension);
        registrations.computeIfAbsent(extensionPoint, ignored -> new CopyOnWriteArrayList<>()).add(registration);
        return () -> registrations.computeIfPresent(extensionPoint, (ignored, values) -> {
            values.remove(registration);
            return values.isEmpty() ? null : values;
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I> List<I> extensions(Class<I> extensionPoint) {
        List<Registration> values = registrations.get(extensionPoint);
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<Registration> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.comparingInt(Registration::priority).thenComparingLong(Registration::sequence));
        return (List<I>) sorted.stream().map(Registration::extension).toList();
    }

    private record Registration(String pluginCode, int priority, long sequence, Object extension) {
    }
}
