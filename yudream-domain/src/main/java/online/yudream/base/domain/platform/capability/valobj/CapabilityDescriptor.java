package online.yudream.base.domain.platform.capability.valobj;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;

import java.util.List;
import java.util.Map;

public record CapabilityDescriptor(
        String code,
        String name,
        CapabilityType type,
        String description,
        String icon,
        int sort,
        Map<String, String> defaultConfig,
        List<String> dependencies
) {
    public CapabilityDescriptor(
            String code,
            String name,
            CapabilityType type,
            String description,
            String icon,
            int sort,
            Map<String, String> defaultConfig
    ) {
        this(code, name, type, description, icon, sort, defaultConfig, List.of());
    }

    public CapabilityDescriptor {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
