package online.yudream.base.infra.platform.plugin.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.infra.platform.satori.service.SpringSatoriEventPublisher.SatoriPublishedEvent;
import online.yudream.base.plugin.spi.system.messaging.PluginEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SatoriPluginEventDispatcher {
    private final JarPluginRuntimeGateway pluginRuntimeGateway;

    @EventListener
    public void dispatch(SatoriPublishedEvent published) {
        var event = published.event();
        pluginRuntimeGateway.publishSatoriEvent(new PluginEvent(
                event.sn(), event.type(), event.platform(), event.selfId(),
                event.channel() == null ? null : event.channel().id(),
                event.message() == null ? null : event.message().content(),
                event.button() == null ? null : event.button().id(),
                event.argv() == null ? null : event.argv().name(),
                event.referrer(), event.extensionType(), event.extensionData()));
    }
}
