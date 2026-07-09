package online.yudream.base.plugin.projectprogress.application.service;

import online.yudream.base.plugin.projectprogress.application.assembler.ProjectProgressAppAssembler;
import online.yudream.base.plugin.projectprogress.domain.aggregate.ProjectProgressEvent;
import online.yudream.base.plugin.spi.http.PluginSseStream;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ProjectProgressEventStream implements PluginSseStream {

    private final Set<Subscriber> subscribers = ConcurrentHashMap.newKeySet();
    private final ProjectProgressAppAssembler assembler = new ProjectProgressAppAssembler();

    @Override
    public void subscribe(Subscriber subscriber) {
        subscribers.add(subscriber);
        subscriber.send("project-progress.connected", java.util.Map.of("connected", true));
    }

    @Override
    public void unsubscribe(Subscriber subscriber) {
        subscribers.remove(subscriber);
    }

    public void publish(ProjectProgressEvent event) {
        Object payload = assembler.toDTO(event);
        for (Subscriber subscriber : subscribers) {
            try {
                subscriber.send("project-progress.event", payload);
            } catch (RuntimeException ignored) {
                subscribers.remove(subscriber);
            }
        }
    }
}
