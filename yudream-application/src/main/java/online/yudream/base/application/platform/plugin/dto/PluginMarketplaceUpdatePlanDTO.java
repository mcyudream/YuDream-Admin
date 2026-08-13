package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketplaceUpdatePlanDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String fromVersion;
    private String toVersion;
    private String changeType;
    private List<PluginStorePluginDependencyDTO> requiredDependencies;
    private List<PluginStorePluginDependencyDTO> optionalDependencies;
    private List<String> affectedEnabledPlugins;
    private boolean requiresRestart;
    private String blockedReason;
    private List<String> warnings;
}
