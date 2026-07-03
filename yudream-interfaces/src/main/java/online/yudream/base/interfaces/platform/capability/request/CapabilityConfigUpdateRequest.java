package online.yudream.base.interfaces.platform.capability.request;

import lombok.Data;

import java.util.Map;

@Data
public class CapabilityConfigUpdateRequest {
    private Map<String, String> config;
}
