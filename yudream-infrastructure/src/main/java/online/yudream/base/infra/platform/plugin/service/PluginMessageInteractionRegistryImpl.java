package online.yudream.base.infra.platform.plugin.service;

import lombok.extern.slf4j.Slf4j;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import online.yudream.base.plugin.spi.system.messaging.PluginInteractionFilter;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageHandler;
import online.yudream.base.plugin.spi.system.messaging.PluginMessageInteractionRegistry;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PluginMessageInteractionRegistryImpl implements PluginMessageInteractionRegistry, AutoCloseable {
    private final String pluginCode;
    private final Map<Kind, List<Registration>> registrations = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2, Thread.ofVirtual().name("plugin-message-" + System.nanoTime(), 0).factory());

    public PluginMessageInteractionRegistryImpl(String pluginCode) {
        this.pluginCode = pluginCode;
    }

    @Override public AutoCloseable onMessage(PluginInteractionFilter filter, PluginMessageHandler handler) { return add(Kind.MESSAGE, filter, handler); }
    @Override public AutoCloseable onNative(PluginInteractionFilter filter, PluginMessageHandler handler) { return add(Kind.NATIVE, filter, handler); }
    @Override public AutoCloseable onCommand(String command, PluginMessageHandler handler) { return add(Kind.COMMAND, new PluginInteractionFilter(java.util.Set.of(), null, null, command), handler); }
    @Override public AutoCloseable onButton(String buttonId, PluginMessageHandler handler) { return add(Kind.BUTTON, new PluginInteractionFilter(java.util.Set.of(), null, null, buttonId), handler); }
    @Override public AutoCloseable beforeSend(PluginMessageHandler handler) { return add(Kind.BEFORE_SEND, null, handler); }
    @Override public AutoCloseable afterSend(PluginMessageHandler handler) { return add(Kind.AFTER_SEND, null, handler); }

    public void publish(PluginEvent event, boolean nativeEvent) {
        publish(nativeEvent ? Kind.NATIVE : Kind.MESSAGE, event);
        if (StringUtils.hasText(event.command())) publish(Kind.COMMAND, event);
        if (StringUtils.hasText(event.buttonId())) publish(Kind.BUTTON, event);
    }

    public void beforeSend(PluginEvent event) { publish(Kind.BEFORE_SEND, event); }
    public void afterSend(PluginEvent event) { publish(Kind.AFTER_SEND, event); }
    public int count() { return registrations.values().stream().mapToInt(List::size).sum(); }

    private AutoCloseable add(Kind kind, PluginInteractionFilter filter, PluginMessageHandler handler) {
        if (handler == null) throw new IllegalArgumentException("插件消息处理器不能为空");
        Registration registration = new Registration(filter, handler);
        registrations.computeIfAbsent(kind, ignored -> java.util.Collections.synchronizedList(new ArrayList<>())).add(registration);
        return () -> remove(kind, registration);
    }

    private void publish(Kind kind, PluginEvent event) {
        List<Registration> handlers = registrations.getOrDefault(kind, List.of());
        for (Registration registration : List.copyOf(handlers)) {
            if (!matches(kind, registration.filter(), event)) continue;
            executor.submit(() -> invoke(kind, registration.handler(), event));
        }
    }

    private boolean matches(Kind kind, PluginInteractionFilter filter, PluginEvent event) {
        if (filter == null) return true;
        if (!filter.eventTypes().isEmpty() && !filter.eventTypes().contains(event.type())) return false;
        if (StringUtils.hasText(filter.platform()) && !filter.platform().equals(event.platform())) return false;
        if (StringUtils.hasText(filter.channelId()) && !filter.channelId().equals(event.channelId())) return false;
        if (kind == Kind.COMMAND) return filter.command().equals(event.command());
        if (kind == Kind.BUTTON) return filter.command().equals(event.buttonId());
        return true;
    }

    private void invoke(Kind kind, PluginMessageHandler handler, PluginEvent event) {
        try {
            handler.handle(event);
        } catch (Exception ex) {
            log.warn("Plugin message handler failed, plugin={}, kind={}, event={}", pluginCode, kind, event.type(), ex);
        }
    }

    private void remove(Kind kind, Registration registration) {
        List<Registration> handlers = registrations.get(kind);
        if (handlers != null) handlers.remove(registration);
    }

    @Override
    public void close() {
        registrations.clear();
        executor.shutdownNow();
        try { executor.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }

    private enum Kind { MESSAGE, NATIVE, COMMAND, BUTTON, BEFORE_SEND, AFTER_SEND }
    private record Registration(PluginInteractionFilter filter, PluginMessageHandler handler) { }
}
