package online.yudream.base.interfaces.platform.plugin.assembler;

import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationEditCmd;
import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationReviewCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketPublicationEditRequest;
import online.yudream.base.interfaces.platform.plugin.request.PluginMarketPublicationReviewRequest;
import online.yudream.base.interfaces.platform.plugin.res.PluginMarketPublicationRes;

import java.util.List;

public class PluginMarketPublicationWebAssembler {

    private PluginMarketPublicationWebAssembler() {
    }

    public static PluginMarketPublicationReviewCmd toReviewCmd(String id, PluginMarketPublicationReviewRequest request) {
        PluginMarketPublicationReviewCmd cmd = new PluginMarketPublicationReviewCmd();
        cmd.setId(parseId(id));
        cmd.setNote(request == null ? null : request.getNote());
        return cmd;
    }

    public static PluginMarketPublicationEditCmd toEditCmd(String id, PluginMarketPublicationEditRequest request) {
        PluginMarketPublicationEditCmd cmd = new PluginMarketPublicationEditCmd();
        cmd.setId(parseId(id));
        cmd.setDisplayName(request.getDisplayName());
        cmd.setDescription(request.getDescription());
        cmd.setReleaseNotes(request.getReleaseNotes());
        cmd.setLicense(request.getLicense());
        cmd.setCategory(request.getCategory());
        cmd.setTags(request.getTags());
        cmd.setCompatibility(request.getCompatibility());
        return cmd;
    }

    public static PluginMarketPublicationRes toRes(PluginMarketPublicationDTO dto) {
        return PluginMarketPublicationRes.builder()
                .id(dto.getId() == null ? null : dto.getId().toString())
                .code(dto.getCode())
                .pluginVersion(dto.getPluginVersion())
                .displayName(dto.getDisplayName())
                .description(dto.getDescription())
                .mainClass(dto.getMainClass())
                .dependencies(dto.getDependencies())
                .softDependencies(dto.getSoftDependencies())
                .icon(dto.getIcon())
                .releaseNotes(dto.getReleaseNotes())
                .license(dto.getLicense())
                .category(dto.getCategory())
                .tags(dto.getTags())
                .compatibility(dto.getCompatibility())
                .sha256(dto.getSha256())
                .sizeBytes(dto.getSizeBytes())
                .downloadCount(dto.getDownloadCount())
                .publisherUserId(dto.getPublisherUserId())
                .channel(dto.getChannel())
                .status(dto.getStatus())
                .reviewNote(dto.getReviewNote())
                .reviewerUserId(dto.getReviewerUserId())
                .reviewedAt(dto.getReviewedAt())
                .createTime(dto.getCreateTime())
                .build();
    }

    public static List<PluginMarketPublicationRes> toResList(List<PluginMarketPublicationDTO> items) {
        return items == null ? List.of() : items.stream().map(PluginMarketPublicationWebAssembler::toRes).toList();
    }

    public static Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new BizException("发布物不存在");
        }
    }
}
