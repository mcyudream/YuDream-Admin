package online.yudream.base.infra.platform.plugin.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "yudream.platform.plugin")
public class PluginProperties {

    private boolean enabled = true;
    private List<String> directories = new ArrayList<>(List.of(
            "plugins",
            "yudream-sample-plugin/target",
            "yudream-plugin-blessing-skin/target",
            "yudream-plugin-authlib-injector/target"
    ));
}
