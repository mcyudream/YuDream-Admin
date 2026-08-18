package online.yudream.base.application.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiIngestTaskDTO;
import online.yudream.base.application.platform.wiki.dto.WikiLintReportDTO;
import online.yudream.base.application.platform.wiki.dto.WikiReviewItemDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSourceDTO;
import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.valobj.WikiLintReport;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;

import java.util.List;

public final class WikiKnowledgeAssembler {

    private WikiKnowledgeAssembler() {
    }

    public static WikiSourceDTO source(WikiSource source) {
        if (source == null) {
            return null;
        }
        return new WikiSourceDTO(
                id(source.getId()),
                id(source.getSpaceId()),
                source.getFolderPath(),
                source.getFileName(),
                source.getTitle(),
                source.getKind() == null ? null : source.getKind().name(),
                source.getUrl(),
                source.getMimeType(),
                source.getFormat() == null ? null : source.getFormat().name(),
                id(source.getFileObjectId()),
                source.getContentHash(),
                source.getExtractedText(),
                source.getExtractionStatus() == null ? null : source.getExtractionStatus().name(),
                source.getExtractionError(),
                images(source.getImages()),
                source.getIngestStatus() == null ? null : source.getIngestStatus().name(),
                source.getIngestError(),
                source.getIngestedAt(),
                source.getSort(),
                source.getFileObjectId() == null ? null : fileUrl(source.getFileObjectId())
        );
    }

    public static WikiIngestTaskDTO ingestTask(WikiIngestTask task) {
        if (task == null) {
            return null;
        }
        return new WikiIngestTaskDTO(
                id(task.getId()),
                id(task.getSpaceId()),
                id(task.getSourceId()),
                task.getTaskType() == null ? null : task.getTaskType().name(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getAttempts(),
                task.getMaxAttempts(),
                task.getErrorMessage(),
                task.getPhase(),
                task.getPercent(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getSortOrder(),
                task.getPayloadJson()
        );
    }

    public static WikiReviewItemDTO reviewItem(WikiReviewItem item) {
        if (item == null) {
            return null;
        }
        return new WikiReviewItemDTO(
                id(item.getId()),
                id(item.getSpaceId()),
                id(item.getSourceId()),
                item.getItemType() == null ? null : item.getItemType().name(),
                item.getTitle(),
                item.getDescription(),
                item.getSuggestedAction(),
                item.getSearchQueries(),
                item.getPageTitles(),
                item.getStatus() == null ? null : item.getStatus().name(),
                item.getResolvedAt(),
                item.getCreateTime()
        );
    }

    public static WikiLintReportDTO lintReport(WikiLintReport report) {
        if (report == null) {
            return null;
        }
        return new WikiLintReportDTO(
                report.generatedAt(),
                report.summary(),
                report.issues().stream().map(issue -> new WikiLintReportDTO.Issue(
                        issue.category(),
                        issue.severity(),
                        issue.title(),
                        issue.description(),
                        issue.pageTitles(),
                        issue.suggestedAction(),
                        issue.searchQueries()
                )).toList()
        );
    }

    private static List<WikiSourceDTO.Image> images(List<WikiSourceImage> images) {
        if (images == null) {
            return List.of();
        }
        return images.stream().map(WikiKnowledgeAssembler::image).toList();
    }

    private static WikiSourceDTO.Image image(WikiSourceImage image) {
        if (image == null) {
            return null;
        }
        return new WikiSourceDTO.Image(
                id(image.fileObjectId()),
                image.pageNumber(),
                image.sequence(),
                image.caption(),
                image.captionStatus() == null ? null : image.captionStatus().name(),
                image.captionProviderCode(),
                image.captionModelCode(),
                image.width(),
                image.height(),
                image.contentType(),
                image.fileObjectId() == null ? null : fileUrl(image.fileObjectId())
        );
    }

    private static String fileUrl(Long fileObjectId) {
        return "/api/files/" + fileObjectId + "/content";
    }

    private static String id(Long id) {
        return id == null ? null : id.toString();
    }
}
