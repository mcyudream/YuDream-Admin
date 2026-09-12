package online.yudream.base.infra.platform.plugin.mapper;

import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;
import online.yudream.base.infra.platform.plugin.dataobj.PluginMarketPublicationDO;

public class PluginMarketPublicationInfraMapper {

    private PluginMarketPublicationInfraMapper() {
    }

    public static PluginMarketPublicationDO toDataObj(PluginMarketPublication publication) {
        if (publication == null) {
            return null;
        }
        PluginMarketPublicationDO dataObj = new PluginMarketPublicationDO();
        dataObj.setId(publication.getId());
        dataObj.setCode(publication.getCode());
        dataObj.setPluginVersion(publication.getPluginVersion());
        dataObj.setDisplayName(publication.getDisplayName());
        dataObj.setDescription(publication.getDescription());
        dataObj.setMainClass(publication.getMainClass());
        dataObj.setDependencies(publication.getDependencies());
        dataObj.setSoftDependencies(publication.getSoftDependencies());
        dataObj.setIcon(publication.getIcon());
        dataObj.setReleaseNotes(publication.getReleaseNotes());
        dataObj.setLicense(publication.getLicense());
        dataObj.setCategory(publication.getCategory());
        dataObj.setTags(publication.getTags());
        dataObj.setCompatibilityJson(publication.getCompatibilityJson());
        dataObj.setPublisherJson(publication.getPublisherJson());
        dataObj.setDescriptorJson(publication.getDescriptorJson());
        dataObj.setJarPath(publication.getJarPath());
        dataObj.setSha256(publication.getSha256());
        dataObj.setSizeBytes(publication.getSizeBytes());
        dataObj.setDownloadCount(publication.getDownloadCount());
        dataObj.setPublisherUserId(publication.getPublisherUserId());
        dataObj.setChannel(publication.getChannel());
        dataObj.setStatus(publication.getStatus());
        dataObj.setReviewNote(publication.getReviewNote());
        dataObj.setReviewerUserId(publication.getReviewerUserId());
        dataObj.setReviewedAt(publication.getReviewedAt());
        dataObj.setVersion(publication.getVersion());
        dataObj.setCreateTime(publication.getCreateTime());
        dataObj.setUpdateTime(publication.getUpdateTime());
        return dataObj;
    }

    public static PluginMarketPublication toDomain(PluginMarketPublicationDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return PluginMarketPublication.builder()
                .id(dataObj.getId())
                .code(dataObj.getCode())
                .pluginVersion(dataObj.getPluginVersion())
                .displayName(dataObj.getDisplayName())
                .description(dataObj.getDescription())
                .mainClass(dataObj.getMainClass())
                .dependencies(dataObj.getDependencies())
                .softDependencies(dataObj.getSoftDependencies())
                .icon(dataObj.getIcon())
                .releaseNotes(dataObj.getReleaseNotes())
                .license(dataObj.getLicense())
                .category(dataObj.getCategory())
                .tags(dataObj.getTags())
                .compatibilityJson(dataObj.getCompatibilityJson())
                .publisherJson(dataObj.getPublisherJson())
                .descriptorJson(dataObj.getDescriptorJson())
                .jarPath(dataObj.getJarPath())
                .sha256(dataObj.getSha256())
                .sizeBytes(dataObj.getSizeBytes())
                .downloadCount(dataObj.getDownloadCount())
                .publisherUserId(dataObj.getPublisherUserId())
                .channel(dataObj.getChannel())
                .status(dataObj.getStatus())
                .reviewNote(dataObj.getReviewNote())
                .reviewerUserId(dataObj.getReviewerUserId())
                .reviewedAt(dataObj.getReviewedAt())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
