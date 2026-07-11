package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SatoriEventDetailDTO {
    private String sequence;
    private String type;
    private String rawData;
    private LocalDateTime receivedAt;
}
