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
public class PluginMarketplaceUpdateRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String currentVersion;
    private String latestVersion;
    private String latestReleaseVersion;
    private String latestDisplayName;
    private boolean updateAvailable;
    private boolean compatible;
    private String blockedReason;
}
