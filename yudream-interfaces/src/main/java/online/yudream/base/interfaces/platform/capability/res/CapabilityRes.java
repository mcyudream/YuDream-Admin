package online.yudream.base.interfaces.platform.capability.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityStatus;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class CapabilityRes {
    private String code;
    private String name;
    private CapabilityType type;
    private String description;
    private String icon;
    private Integer sort;
    private Boolean enabled;
    private Map<String, String> config;
    private CapabilityStatus status;
    private String healthMessage;
    private LocalDateTime checkedAt;
    private Map<String, Object> metrics;
}
