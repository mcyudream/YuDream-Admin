package online.yudream.base.domain.platform.wiki.service;

import online.yudream.base.domain.platform.wiki.valobj.WikiPublicationProgress;

import java.util.function.Consumer;

public interface WikiPublicationProgressGateway {
    void publish(WikiPublicationProgress progress);

    AutoCloseable subscribe(Long nodeId, Consumer<WikiPublicationProgress> consumer);
}
