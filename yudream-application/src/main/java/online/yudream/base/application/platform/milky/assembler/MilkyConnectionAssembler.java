package online.yudream.base.application.platform.milky.assembler;

import online.yudream.base.application.platform.milky.dto.MilkyConnectionDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;

public final class MilkyConnectionAssembler {
    private MilkyConnectionAssembler() { }

    public static MilkyConnectionDTO toDTO(MilkyConnection source) {
        if (source == null) {
            return null;
        }
        return MilkyConnectionDTO.builder()
                .id(source.getId())
                .name(source.getName())
                .protocol(source.protocolCode())
                .baseUrl(source.getBaseUrl())
                .appId(source.getAppId())
                .sandbox(source.isSandbox())
                .intents(source.getIntents())
                .mentionOpenIds(source.officialMentionOpenIds())
                .enabled(source.isEnabled())
                .credentialConfigured(source.credentialConfigured())
                .commandMenuImageMode(source.getCommandMenuImageMode())
                .commandMenuPublicBaseUrl(source.getCommandMenuPublicBaseUrl())
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .build();
    }

    public static PageResult<MilkyConnectionDTO> toDTO(PageResult<MilkyConnection> source) {
        return new PageResult<>(source.getRecords().stream().map(MilkyConnectionAssembler::toDTO).toList(),
                source.getTotal(), source.getPage(), source.getSize());
    }
}
