package online.yudream.base.domain.platform.plugin.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.domain.platform.plugin.valobj.PluginDescriptorInfo;
import online.yudream.base.domain.platform.plugin.valobj.PluginFrontendSortSetting;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PluginModule extends BaseDomain {

    private String code;
    private String name;
    private String pluginVersion;
    private String description;
    private String mainClass;
    private String jarPath;
    private List<String> dependencies;
    private PluginStatus status;
    private String errorMessage;
    private LocalDateTime loadedAt;
    private LocalDateTime enabledAt;
    private List<PluginFrontendSortSetting> frontendSortSettings;

    public static PluginModule fromDescriptor(PluginDescriptorInfo descriptor) {
        return PluginModule.builder()
                .code(descriptor.code())
                .name(descriptor.name())
                .pluginVersion(descriptor.version())
                .description(descriptor.description())
                .mainClass(descriptor.mainClass())
                .jarPath(descriptor.jarPath())
                .dependencies(descriptor.dependencies())
                .status(PluginStatus.INSTALLED)
                .build();
    }

    public void refreshDescriptor(PluginDescriptorInfo descriptor) {
        this.name = descriptor.name();
        this.pluginVersion = descriptor.version();
        this.description = descriptor.description();
        this.mainClass = descriptor.mainClass();
        this.jarPath = descriptor.jarPath();
        this.dependencies = descriptor.dependencies();
        if (this.status == null) {
            this.status = PluginStatus.INSTALLED;
        }
    }

    public void markLoaded() {
        this.status = PluginStatus.LOADED;
        this.errorMessage = null;
        this.loadedAt = LocalDateTime.now();
    }

    public void markEnabled() {
        this.status = PluginStatus.ENABLED;
        this.errorMessage = null;
        this.enabledAt = LocalDateTime.now();
    }

    public void markDisabled() {
        this.status = PluginStatus.DISABLED;
        this.enabledAt = null;
    }

    public void markUnloaded() {
        this.status = PluginStatus.INSTALLED;
        this.loadedAt = null;
        this.enabledAt = null;
    }

    public void markError(String message) {
        this.status = PluginStatus.ERROR;
        this.errorMessage = message;
    }

    public PluginFrontendSortSetting frontendSortSetting(String moduleName) {
        String target = normalize(moduleName);
        return (frontendSortSettings == null ? List.<PluginFrontendSortSetting>of() : frontendSortSettings).stream()
                .filter(setting -> Objects.equals(normalize(setting.moduleName()), target))
                .findFirst()
                .orElse(null);
    }

    public void saveFrontendSortSetting(PluginFrontendSortSetting setting) {
        if (setting == null) {
            return;
        }
        String target = normalize(setting.moduleName());
        List<PluginFrontendSortSetting> next = new ArrayList<>();
        for (PluginFrontendSortSetting item : frontendSortSettings == null ? List.<PluginFrontendSortSetting>of() : frontendSortSettings) {
            if (!Objects.equals(normalize(item.moduleName()), target)) {
                next.add(item);
            }
        }
        next.add(setting);
        this.frontendSortSettings = next;
    }

    public boolean enabled() {
        return this.status == PluginStatus.ENABLED;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
