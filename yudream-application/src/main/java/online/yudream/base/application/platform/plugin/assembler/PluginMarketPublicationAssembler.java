package online.yudream.base.application.platform.plugin.assembler;

import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.domain.platform.plugin.aggregate.PluginMarketPublication;

import java.util.List;

public class PluginMarketPublicationAssembler {

    private PluginMarketPublicationAssembler() {
    }

    public static PluginMarketPublicationDTO toDTO(PluginMarketPublication publication) {
        return PluginMarketPublicationDTO.builder()
                .id(publication.getId())
                .code(publication.getCode())
                .pluginVersion(publication.getPluginVersion())
                .displayName(publication.getDisplayName())
                .description(publication.getDescription())
                .mainClass(publication.getMainClass())
                .dependencies(publication.getDependencies())
                .softDependencies(publication.getSoftDependencies())
                .icon(publication.getIcon())
                .releaseNotes(publication.getReleaseNotes())
                .license(publication.getLicense())
                .category(publication.getCategory())
                .tags(publication.getTags())
                .sha256(publication.getSha256())
                .sizeBytes(publication.getSizeBytes())
                .downloadCount(publication.getDownloadCount())
                .publisherUserId(publication.getPublisherUserId())
                .channel(publication.getChannel())
                .status(publication.getStatus())
                .reviewNote(publication.getReviewNote())
                .reviewerUserId(publication.getReviewerUserId())
                .reviewedAt(publication.getReviewedAt())
                .createTime(publication.getCreateTime())
                .build();
    }

    public static List<PluginMarketPublicationDTO> toDTOList(List<PluginMarketPublication> publications) {
        return publications == null ? List.of() : publications.stream()
                .map(PluginMarketPublicationAssembler::toDTO)
                .toList();
    }
}
