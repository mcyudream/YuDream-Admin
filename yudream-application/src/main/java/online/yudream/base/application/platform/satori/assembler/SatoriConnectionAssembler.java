package online.yudream.base.application.platform.satori.assembler;

import online.yudream.base.application.platform.satori.dto.SatoriConnectionDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConnectionTestDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriMeta;

import java.time.LocalDateTime;

public final class SatoriConnectionAssembler {
    private SatoriConnectionAssembler() {
    }

    public static SatoriConnectionDTO toDTO(SatoriConnection connection) {
        if (connection == null) {
            return null;
        }
        return SatoriConnectionDTO.builder()
                .id(connection.getId())
                .name(connection.getName())
                .baseUrl(connection.getBaseUrl())
                .enabled(connection.enabled())
                .credentialConfigured(connection.getToken() != null && !connection.getToken().isBlank())
                .createTime(connection.getCreateTime())
                .updateTime(connection.getUpdateTime())
                .build();
    }

    public static PageResult<SatoriConnectionDTO> toDTO(PageResult<SatoriConnection> result) {
        return new PageResult<>(result.getRecords().stream().map(SatoriConnectionAssembler::toDTO).toList(),
                result.getTotal(), result.getPage(), result.getSize());
    }

    public static SatoriConnectionTestDTO toTestDTO(SatoriMeta meta) {
        return SatoriConnectionTestDTO.builder()
                .success(true)
                .impl(meta.impl())
                .protocolVersion(meta.protocolVersion())
                .adapter(meta.adapter())
                .features(meta.features())
                .testedAt(LocalDateTime.now())
                .build();
    }
}
