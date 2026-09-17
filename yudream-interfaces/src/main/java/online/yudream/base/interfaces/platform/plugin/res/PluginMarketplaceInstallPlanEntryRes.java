package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketplaceInstallPlanEntryRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String displayName;
    private boolean required;
    private String range;
    private boolean installed;
    private String installedVersion;
    private boolean versionSatisfied;
    private boolean storeAvailable;
    private String storeVersion;
    private String storeSourceCode;
    private String storeSourceName;
    private boolean installable;
    private String installDisabledReason;
}
