package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginThemeRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String code;
    private String name;
    private String description;
    private Set<String> scopes;
    private List<String> styles;
    private String preview;
    private String assetRevision;
}
