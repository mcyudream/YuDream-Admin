package online.yudream.base.application.platform.capability.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CapabilityTestDTO {
    private Boolean success;
    private String message;
    private LocalDateTime testedAt;
}
