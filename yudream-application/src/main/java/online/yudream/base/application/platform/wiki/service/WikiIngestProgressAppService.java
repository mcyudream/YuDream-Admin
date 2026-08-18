package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.dto.WikiIngestProgressDTO;
import online.yudream.base.domain.platform.wiki.service.WikiIngestProgressGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class WikiIngestProgressAppService {

    private final CapabilityAppService capabilities;
    private final WikiIngestProgressGateway progress;

    public AutoCloseable subscribe(Long spaceId, Consumer<WikiIngestProgressDTO> consumer) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        return progress.subscribe(spaceId, item -> consumer.accept(toDto(item)));
    }

    private WikiIngestProgressDTO toDto(WikiIngestProgress item) {
        return new WikiIngestProgressDTO(
                id(item.taskId()),
                id(item.spaceId()),
                id(item.sourceId()),
                item.phase(),
                item.message(),
                item.percent(),
                item.completed());
    }

    private String id(Long value) {
        return value == null ? null : value.toString();
    }
}
