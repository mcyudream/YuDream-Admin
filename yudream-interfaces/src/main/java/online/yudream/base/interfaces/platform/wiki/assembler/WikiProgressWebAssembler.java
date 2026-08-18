package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiIngestProgressDTO;
import online.yudream.base.application.platform.wiki.dto.WikiPublicationProgressDTO;
import online.yudream.base.interfaces.platform.wiki.res.WikiIngestProgressRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiPublicationProgressRes;

public final class WikiProgressWebAssembler {
    private WikiProgressWebAssembler() {
    }

    public static WikiPublicationProgressRes toRes(WikiPublicationProgressDTO value) {
        return WikiPublicationProgressRes.builder().event("wiki.progress").action(value.phase()).module("wiki")
                .nodeId(value.nodeId()).versionId(value.versionId())
                .phase(value.phase()).message(value.message()).percent(value.percent()).completed(value.completed())
                .timestamp(System.currentTimeMillis()).build();
    }

    public static WikiIngestProgressRes toRes(WikiIngestProgressDTO value) {
        return WikiIngestProgressRes.builder()
                .event("wiki.ingest")
                .action(value.phase())
                .module("wiki")
                .taskId(value.taskId())
                .spaceId(value.spaceId())
                .sourceId(value.sourceId())
                .phase(value.phase())
                .message(value.message())
                .percent(value.percent())
                .completed(value.completed())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
