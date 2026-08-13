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
public class PluginStorePluginVersionRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String releaseVersion;
    private PluginStorePluginDescriptorRes descriptor;
    private boolean installable;
    private String installDisabledReason;
}
