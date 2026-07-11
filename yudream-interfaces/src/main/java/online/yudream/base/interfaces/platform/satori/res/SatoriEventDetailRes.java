package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SatoriEventDetailRes {
    private String sequence;
    private String type;
    private String rawData;
    private LocalDateTime receivedAt;
}
