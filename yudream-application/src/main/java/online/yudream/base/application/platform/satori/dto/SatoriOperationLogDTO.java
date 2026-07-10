package online.yudream.base.application.platform.satori.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SatoriOperationLogDTO {
    private Long id;
    private String level;
    private String category;
    private String action;
    private String detail;
    private LocalDateTime occurredAt;
}
