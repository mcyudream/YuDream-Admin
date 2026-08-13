package online.yudream.base.interfaces.platform.plugin.res;

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
public class PluginMarketplaceUpdatePlanRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String fromVersion;
    private String toVersion;
    private String changeType;
    private List<PluginStorePluginDependencyRes> requiredDependencies;
    private List<PluginStorePluginDependencyRes> optionalDependencies;
    private List<String> affectedEnabledPlugins;
    private boolean requiresRestart;
    private String blockedReason;
    private List<String> warnings;
}
