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
public class PluginStorePluginJarDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String mavenCoordinates;
    private String url;
    private String sha256;
}
