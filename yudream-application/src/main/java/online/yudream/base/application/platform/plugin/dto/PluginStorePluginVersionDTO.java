package online.yudream.base.application.platform.plugin.dto;

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
public class PluginStorePluginVersionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String releaseVersion;
    private PluginStorePluginDescriptorDTO descriptor;
    private boolean installable;
    private String installDisabledReason;
}
