package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SatoriOperationLogRes {
    private String id;
    private String level;
    private String category;
    private String action;
    private String detail;
    private LocalDateTime occurredAt;
}
