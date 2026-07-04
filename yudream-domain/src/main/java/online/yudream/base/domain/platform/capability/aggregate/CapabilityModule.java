package online.yudream.base.domain.platform.capability.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CapabilityModule extends BaseDomain {

    private String code;
    private String name;
    private CapabilityType type;
    private String description;
    private String icon;
    private Integer sort;
    private Boolean enabled;
    private Map<String, String> config;
    private List<String> dependencies;

    public static CapabilityModule fromDescriptor(CapabilityDescriptor descriptor) {
        return CapabilityModule.builder()
                .code(descriptor.code())
                .name(descriptor.name())
                .type(descriptor.type())
                .description(descriptor.description())
                .icon(descriptor.icon())
                .sort(descriptor.sort())
                .enabled(false)
                .config(new HashMap<>(descriptor.defaultConfig() == null ? Map.of() : descriptor.defaultConfig()))
                .dependencies(descriptor.dependencies())
                .build();
    }

    public void refreshDescriptor(CapabilityDescriptor descriptor) {
        this.name = descriptor.name();
        this.type = descriptor.type();
        this.description = descriptor.description();
        this.icon = descriptor.icon();
        this.sort = descriptor.sort();
        this.dependencies = descriptor.dependencies();
        if (this.enabled == null) {
            this.enabled = false;
        }
        if (this.config == null || this.config.isEmpty()) {
            this.config = new HashMap<>(descriptor.defaultConfig() == null ? Map.of() : descriptor.defaultConfig());
        }
    }

    public void updateConfig(Map<String, String> config) {
        this.config = new HashMap<>(config == null ? Map.of() : config);
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
