package online.yudream.base.application.platform.satori.assembler;

import online.yudream.base.application.platform.satori.dto.SatoriConnectionDTO;
import online.yudream.base.application.platform.satori.dto.SatoriConnectionTestDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.model.SatoriModels.SatoriLogin;

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
                .platform(connection.getPlatform())
                .userId(connection.getUserId())
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

    public static SatoriConnectionTestDTO toTestDTO(SatoriConnection connection, SatoriLogin login) {
        return SatoriConnectionTestDTO.builder()
                .success(true)
                .platform(connection.getPlatform())
                .userId(connection.getUserId())
                .status(login == null || login.status() == null ? null : login.status().name())
                .adapter(login == null ? null : login.adapter())
                .features(login == null ? java.util.List.of() : login.features())
                .testedAt(LocalDateTime.now())
                .build();
    }
}
