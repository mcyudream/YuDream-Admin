package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.domain.platform.wiki.valobj.WikiPublicationProgress;
import online.yudream.base.interfaces.platform.wiki.res.WikiPublicationProgressRes;

public final class WikiProgressWebAssembler {
    private WikiProgressWebAssembler() {
    }

    public static WikiPublicationProgressRes toRes(WikiPublicationProgress value) {
        return WikiPublicationProgressRes.builder().event("wiki.progress").action(value.phase()).module("wiki")
                .nodeId(String.valueOf(value.nodeId())).versionId(value.versionId() == null ? null : String.valueOf(value.versionId()))
                .phase(value.phase()).message(value.message()).percent(value.percent()).completed(value.completed())
                .timestamp(System.currentTimeMillis()).build();
    }
}
