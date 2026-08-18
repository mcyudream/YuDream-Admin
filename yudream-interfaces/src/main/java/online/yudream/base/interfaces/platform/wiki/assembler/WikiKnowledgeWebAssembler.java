package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.application.platform.wiki.dto.WikiGraphSnapshotDTO;
import online.yudream.base.application.platform.wiki.dto.WikiIngestTaskDTO;
import online.yudream.base.application.platform.wiki.dto.WikiLintReportDTO;
import online.yudream.base.application.platform.wiki.dto.WikiResearchPlanDTO;
import online.yudream.base.application.platform.wiki.dto.WikiReviewItemDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSourceDTO;
import online.yudream.base.interfaces.platform.wiki.res.WikiChatResultRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiGraphSnapshotRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiIngestTaskRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiLintReportRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiResearchPlanRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiReviewItemRes;
import online.yudream.base.interfaces.platform.wiki.res.WikiSourceRes;

import java.util.List;

/**
 * Wiki 知识域接口 DTO 到响应体装配。响应体统一使用接口 res 记录，Snowflake/Long ID 保持字符串。
 */
public final class WikiKnowledgeWebAssembler {

    private WikiKnowledgeWebAssembler() {
    }

    public static WikiSourceRes toRes(WikiSourceDTO dto) {
        return WikiSourceRes.builder()
                .id(dto.id())
                .spaceId(dto.spaceId())
                .folderPath(dto.folderPath())
                .fileName(dto.fileName())
                .title(dto.title())
                .kind(dto.kind())
                .url(dto.url())
                .mimeType(dto.mimeType())
                .format(dto.format())
                .fileObjectId(dto.fileObjectId())
                .contentHash(dto.contentHash())
                .extractedText(dto.extractedText())
                .extractionStatus(dto.extractionStatus())
                .extractionError(dto.extractionError())
                .images(toSourceImages(dto.images()))
                .ingestStatus(dto.ingestStatus())
                .ingestError(dto.ingestError())
                .ingestedAt(dto.ingestedAt())
                .sort(dto.sort())
                .fileUrl(dto.fileUrl())
                .build();
    }

    public static List<WikiSourceRes> toSourceResList(List<WikiSourceDTO> items) {
        return items == null ? List.of() : items.stream().map(WikiKnowledgeWebAssembler::toRes).toList();
    }

    public static WikiGraphSnapshotRes toRes(WikiGraphSnapshotDTO dto) {
        return WikiGraphSnapshotRes.builder()
                .nodes(toGraphNodes(dto.nodes()))
                .edges(toGraphEdges(dto.edges()))
                .communities(toGraphCommunities(dto.communities()))
                .insights(toGraphInsights(dto.insights()))
                .build();
    }

    public static WikiIngestTaskRes toRes(WikiIngestTaskDTO dto) {
        return WikiIngestTaskRes.builder()
                .id(dto.id())
                .spaceId(dto.spaceId())
                .sourceId(dto.sourceId())
                .taskType(dto.taskType())
                .status(dto.status())
                .attempts(dto.attempts())
                .maxAttempts(dto.maxAttempts())
                .errorMessage(dto.errorMessage())
                .phase(dto.phase())
                .percent(dto.percent())
                .startedAt(dto.startedAt())
                .finishedAt(dto.finishedAt())
                .sortOrder(dto.sortOrder())
                .payloadJson(dto.payloadJson())
                .build();
    }

    public static List<WikiIngestTaskRes> toIngestTaskResList(List<WikiIngestTaskDTO> items) {
        return items == null ? List.of() : items.stream().map(WikiKnowledgeWebAssembler::toRes).toList();
    }

    public static WikiReviewItemRes toRes(WikiReviewItemDTO dto) {
        return WikiReviewItemRes.builder()
                .id(dto.id())
                .spaceId(dto.spaceId())
                .sourceId(dto.sourceId())
                .itemType(dto.itemType())
                .title(dto.title())
                .description(dto.description())
                .suggestedAction(dto.suggestedAction())
                .searchQueries(dto.searchQueries())
                .pageTitles(dto.pageTitles())
                .status(dto.status())
                .resolvedAt(dto.resolvedAt())
                .createTime(dto.createTime())
                .build();
    }

    public static List<WikiReviewItemRes> toReviewItemResList(List<WikiReviewItemDTO> items) {
        return items == null ? List.of() : items.stream().map(WikiKnowledgeWebAssembler::toRes).toList();
    }

    public static WikiLintReportRes toRes(WikiLintReportDTO dto) {
        return WikiLintReportRes.builder()
                .generatedAt(dto.generatedAt())
                .summary(dto.summary())
                .issues(toLintIssues(dto.issues()))
                .build();
    }

    public static WikiResearchPlanRes toRes(WikiResearchPlanDTO dto) {
        return WikiResearchPlanRes.builder()
                .topic(dto.topic())
                .rationale(dto.rationale())
                .queries(dto.queries())
                .build();
    }

    public static WikiChatResultRes toRes(WikiChatResultDTO dto) {
        return WikiChatResultRes.builder()
                .answer(dto.answer())
                .reasoning(dto.reasoning())
                .citations(toCitations(dto.citations()))
                .build();
    }

    private static List<WikiSourceRes.Image> toSourceImages(List<WikiSourceDTO.Image> items) {
        return items == null ? List.of() : items.stream().map(image -> WikiSourceRes.Image.builder()
                .fileObjectId(image.fileObjectId())
                .pageNumber(image.pageNumber())
                .sequence(image.sequence())
                .caption(image.caption())
                .captionStatus(image.captionStatus())
                .captionProviderCode(image.captionProviderCode())
                .captionModelCode(image.captionModelCode())
                .width(image.width())
                .height(image.height())
                .contentType(image.contentType())
                .url(image.url())
                .build()).toList();
    }

    private static List<WikiGraphSnapshotRes.Node> toGraphNodes(List<WikiGraphSnapshotDTO.Node> items) {
        return items == null ? List.of() : items.stream().map(node -> WikiGraphSnapshotRes.Node.builder()
                .id(node.id())
                .title(node.title())
                .type(node.type())
                .degree(node.degree())
                .community(node.community())
                .build()).toList();
    }

    private static List<WikiGraphSnapshotRes.Edge> toGraphEdges(List<WikiGraphSnapshotDTO.Edge> items) {
        return items == null ? List.of() : items.stream().map(edge -> WikiGraphSnapshotRes.Edge.builder()
                .source(edge.source())
                .target(edge.target())
                .weight(edge.weight())
                .signal(edge.signal())
                .build()).toList();
    }

    private static List<WikiGraphSnapshotRes.Community> toGraphCommunities(List<WikiGraphSnapshotDTO.Community> items) {
        return items == null ? List.of() : items.stream().map(community -> WikiGraphSnapshotRes.Community.builder()
                .id(community.id())
                .label(community.label())
                .nodeIds(community.nodeIds())
                .size(community.size())
                .cohesion(community.cohesion())
                .lowCohesion(community.lowCohesion())
                .build()).toList();
    }

    private static List<WikiGraphSnapshotRes.Insight> toGraphInsights(List<WikiGraphSnapshotDTO.Insight> items) {
        return items == null ? List.of() : items.stream().map(insight -> WikiGraphSnapshotRes.Insight.builder()
                .kind(insight.kind())
                .title(insight.title())
                .description(insight.description())
                .nodeIds(insight.nodeIds())
                .searchQueries(insight.searchQueries())
                .build()).toList();
    }

    private static List<WikiLintReportRes.Issue> toLintIssues(List<WikiLintReportDTO.Issue> items) {
        return items == null ? List.of() : items.stream().map(issue -> WikiLintReportRes.Issue.builder()
                .category(issue.category())
                .severity(issue.severity())
                .title(issue.title())
                .description(issue.description())
                .pageTitles(issue.pageTitles())
                .suggestedAction(issue.suggestedAction())
                .searchQueries(issue.searchQueries())
                .build()).toList();
    }

    private static List<WikiChatResultRes.Citation> toCitations(List<WikiChatResultDTO.Citation> items) {
        return items == null ? List.of() : items.stream().map(citation -> WikiChatResultRes.Citation.builder()
                .title(citation.title())
                .path(citation.path())
                .nodeId(citation.nodeId())
                .excerpt(citation.excerpt())
                .images(citation.images() == null ? List.of() : citation.images().stream()
                        .map(image -> new WikiChatResultRes.Citation.Image(image.url(), image.caption()))
                        .toList())
                .build()).toList();
    }
}
