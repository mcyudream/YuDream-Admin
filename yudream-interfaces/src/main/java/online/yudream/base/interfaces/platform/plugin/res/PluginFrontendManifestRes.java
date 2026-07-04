package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginFrontendManifestRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String sdkVersion;

    @Builder.Default
    private List<PluginFrontendModuleRes> modules = new ArrayList<>();
}
