package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiPublicationProgressDTO;
import online.yudream.base.domain.platform.wiki.service.WikiPublicationProgressGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiPublicationProgress;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class WikiPublicationProgressAppService {

    private final CapabilityAppService capabilities;
    private final WikiPublicationProgressGateway progress;

    public AutoCloseable subscribe(Long nodeId, Consumer<WikiPublicationProgressDTO> consumer) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        return progress.subscribe(nodeId, item -> consumer.accept(toDto(item)));
    }

    private WikiPublicationProgressDTO toDto(WikiPublicationProgress item) {
        return new WikiPublicationProgressDTO(
                id(item.nodeId()),
                id(item.versionId()),
                item.phase(),
                item.message(),
                item.percent(),
                item.completed());
    }

    private String id(Long value) {
        return value == null ? null : value.toString();
    }
}
