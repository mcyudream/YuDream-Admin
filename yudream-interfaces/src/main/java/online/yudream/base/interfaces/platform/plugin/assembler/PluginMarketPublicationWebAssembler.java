package online.yudream.base.interfaces.platform.plugin.assembler;

import online.yudream.base.application.platform.plugin.cmd.PluginMarketPublicationReviewCmd;
import online.yudream.base.application.platform.plugin.dto.PluginMarketPublicationDTO;
import online.yudream.base.domain.common.exception.BizException;
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
                .releaseNotes(dto.getReleaseNotes())
                .license(dto.getLicense())
                .sha256(dto.getSha256())
                .sizeBytes(dto.getSizeBytes())
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
