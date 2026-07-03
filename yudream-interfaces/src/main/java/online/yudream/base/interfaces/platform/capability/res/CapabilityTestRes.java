package online.yudream.base.interfaces.platform.capability.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CapabilityTestRes {
    private Boolean success;
    private String message;
    private LocalDateTime testedAt;
}
