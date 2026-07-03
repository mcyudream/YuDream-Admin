package online.yudream.base.domain.platform.capability.valobj;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;

import java.util.Map;

public record CapabilityDescriptor(
        String code,
        String name,
        CapabilityType type,
        String description,
        String icon,
        int sort,
        Map<String, String> defaultConfig
) {
}
