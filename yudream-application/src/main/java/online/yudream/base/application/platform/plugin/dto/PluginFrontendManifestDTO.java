package online.yudream.base.application.platform.plugin.dto;

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
public class PluginFrontendManifestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private String sdkVersion = "1.0.0";

    @Builder.Default
    private List<PluginFrontendModuleDTO> modules = new ArrayList<>();
}
