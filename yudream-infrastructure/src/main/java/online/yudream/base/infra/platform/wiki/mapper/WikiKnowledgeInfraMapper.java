package online.yudream.base.infra.platform.wiki.mapper;

import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import online.yudream.base.infra.platform.wiki.dataobj.WikiIngestTaskDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiReviewItemDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSourceDO;
import online.yudream.base.infra.platform.wiki.dataobj.WikiSourceImageDO;

import java.util.ArrayList;
import java.util.List;

public final class WikiKnowledgeInfraMapper {

    private WikiKnowledgeInfraMapper() {
    }

    public static WikiSource source(WikiSourceDO dataObj) {
        if (dataObj == null) return null;
        return WikiSource.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .spaceId(dataObj.getSpaceId())
                .folderPath(dataObj.getFolderPath())
                .fileName(dataObj.getFileName())
                .title(dataObj.getTitle())
                .kind(dataObj.getKind())
                .url(dataObj.getUrl())
                .mimeType(dataObj.getMimeType())
                .format(dataObj.getFormat())
                .fileObjectId(dataObj.getFileObjectId())
                .contentHash(dataObj.getContentHash())
                .extractedText(dataObj.getExtractedText())
                .extractionStatus(dataObj.getExtractionStatus())
                .extractionError(dataObj.getExtractionError())
                .images(toImages(dataObj.getImages()))
                .ingestStatus(dataObj.getIngestStatus())
                .ingestHash(dataObj.getIngestHash())
                .ingestError(dataObj.getIngestError())
                .ingestedAt(dataObj.getIngestedAt())
                .sort(dataObj.getSort())
                .build();
    }

    public static WikiSourceDO source(WikiSource domain) {
        if (domain == null) return null;
        WikiSourceDO dataObj = new WikiSourceDO();
        copyBase(domain, dataObj);
        dataObj.setSpaceId(domain.getSpaceId());
        dataObj.setFolderPath(domain.getFolderPath());
        dataObj.setFileName(domain.getFileName());
        dataObj.setTitle(domain.getTitle());
        dataObj.setKind(domain.getKind());
        dataObj.setUrl(domain.getUrl());
        dataObj.setMimeType(domain.getMimeType());
        dataObj.setFormat(domain.getFormat());
        dataObj.setFileObjectId(domain.getFileObjectId());
        dataObj.setContentHash(domain.getContentHash());
        dataObj.setExtractedText(domain.getExtractedText());
        dataObj.setExtractionStatus(domain.getExtractionStatus());
        dataObj.setExtractionError(domain.getExtractionError());
        dataObj.setImages(toImageDOs(domain.getImages()));
        dataObj.setIngestStatus(domain.getIngestStatus());
        dataObj.setIngestHash(domain.getIngestHash());
        dataObj.setIngestError(domain.getIngestError());
        dataObj.setIngestedAt(domain.getIngestedAt());
        dataObj.setSort(domain.getSort());
        return dataObj;
    }

    public static WikiIngestTask ingestTask(WikiIngestTaskDO dataObj) {
        if (dataObj == null) return null;
        return WikiIngestTask.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .spaceId(dataObj.getSpaceId())
                .sourceId(dataObj.getSourceId())
                .taskType(dataObj.getTaskType())
                .status(dataObj.getStatus())
                .attempts(dataObj.getAttempts())
                .maxAttempts(dataObj.getMaxAttempts())
                .errorMessage(dataObj.getErrorMessage())
                .phase(dataObj.getPhase())
                .percent(dataObj.getPercent())
                .startedAt(dataObj.getStartedAt())
                .finishedAt(dataObj.getFinishedAt())
                .sortOrder(dataObj.getSortOrder())
                .payloadJson(dataObj.getPayloadJson())
                .build();
    }

    public static WikiIngestTaskDO ingestTask(WikiIngestTask domain) {
        if (domain == null) return null;
        WikiIngestTaskDO dataObj = new WikiIngestTaskDO();
        copyBase(domain, dataObj);
        dataObj.setSpaceId(domain.getSpaceId());
        dataObj.setSourceId(domain.getSourceId());
        dataObj.setTaskType(domain.getTaskType());
        dataObj.setStatus(domain.getStatus());
        dataObj.setAttempts(domain.getAttempts());
        dataObj.setMaxAttempts(domain.getMaxAttempts());
        dataObj.setErrorMessage(domain.getErrorMessage());
        dataObj.setPhase(domain.getPhase());
        dataObj.setPercent(domain.getPercent());
        dataObj.setStartedAt(domain.getStartedAt());
        dataObj.setFinishedAt(domain.getFinishedAt());
        dataObj.setSortOrder(domain.getSortOrder());
        dataObj.setPayloadJson(domain.getPayloadJson());
        return dataObj;
    }

    public static WikiReviewItem reviewItem(WikiReviewItemDO dataObj) {
        if (dataObj == null) return null;
        return WikiReviewItem.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .spaceId(dataObj.getSpaceId())
                .sourceId(dataObj.getSourceId())
                .itemType(dataObj.getItemType())
                .title(dataObj.getTitle())
                .description(dataObj.getDescription())
                .suggestedAction(dataObj.getSuggestedAction())
                .searchQueries(dataObj.getSearchQueries() == null ? new ArrayList<>() : dataObj.getSearchQueries())
                .pageTitles(dataObj.getPageTitles() == null ? new ArrayList<>() : dataObj.getPageTitles())
                .status(dataObj.getStatus())
                .resolvedAt(dataObj.getResolvedAt())
                .build();
    }

    public static WikiReviewItemDO reviewItem(WikiReviewItem domain) {
        if (domain == null) return null;
        WikiReviewItemDO dataObj = new WikiReviewItemDO();
        copyBase(domain, dataObj);
        dataObj.setSpaceId(domain.getSpaceId());
        dataObj.setSourceId(domain.getSourceId());
        dataObj.setItemType(domain.getItemType());
        dataObj.setTitle(domain.getTitle());
        dataObj.setDescription(domain.getDescription());
        dataObj.setSuggestedAction(domain.getSuggestedAction());
        dataObj.setSearchQueries(domain.getSearchQueries() == null ? new ArrayList<>() : new ArrayList<>(domain.getSearchQueries()));
        dataObj.setPageTitles(domain.getPageTitles() == null ? new ArrayList<>() : new ArrayList<>(domain.getPageTitles()));
        dataObj.setStatus(domain.getStatus());
        dataObj.setResolvedAt(domain.getResolvedAt());
        return dataObj;
    }

    private static List<WikiSourceImage> toImages(List<WikiSourceImageDO> dataObjs) {
        if (dataObjs == null) return new ArrayList<>();
        return dataObjs.stream().map(WikiKnowledgeInfraMapper::image).toList();
    }

    private static List<WikiSourceImageDO> toImageDOs(List<WikiSourceImage> domain) {
        if (domain == null) return new ArrayList<>();
        return domain.stream().map(WikiKnowledgeInfraMapper::image).toList();
    }

    private static WikiSourceImage image(WikiSourceImageDO dataObj) {
        if (dataObj == null) return null;
        return new WikiSourceImage(
                dataObj.getFileObjectId(),
                dataObj.getPageNumber(),
                dataObj.getSequence(),
                dataObj.getCaption(),
                dataObj.getCaptionStatus(),
                dataObj.getCaptionProviderCode(),
                dataObj.getCaptionModelCode(),
                dataObj.getWidth(),
                dataObj.getHeight(),
                dataObj.getContentType()
        );
    }

    private static WikiSourceImageDO image(WikiSourceImage domain) {
        if (domain == null) return null;
        WikiSourceImageDO dataObj = new WikiSourceImageDO();
        dataObj.setFileObjectId(domain.fileObjectId());
        dataObj.setPageNumber(domain.pageNumber());
        dataObj.setSequence(domain.sequence());
        dataObj.setCaption(domain.caption());
        dataObj.setCaptionStatus(domain.captionStatus());
        dataObj.setCaptionProviderCode(domain.captionProviderCode());
        dataObj.setCaptionModelCode(domain.captionModelCode());
        dataObj.setWidth(domain.width());
        dataObj.setHeight(domain.height());
        dataObj.setContentType(domain.contentType());
        return dataObj;
    }

    private static void copyBase(WikiSource source, WikiSourceDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private static void copyBase(WikiIngestTask source, WikiIngestTaskDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private static void copyBase(WikiReviewItem source, WikiReviewItemDO target) {
        target.setId(source.getId());
        target.setVersion(source.getVersion());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }
}
