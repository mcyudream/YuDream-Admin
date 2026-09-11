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
    /** 该版本所在市场源；内置直连路径为 null。 */
    private String sourceCode;
    private String sourceName;
}
