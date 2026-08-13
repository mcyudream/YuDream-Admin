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
    private List<String> directories = new ArrayList<>(List.of("plugins"));
    private String storeRootUrl = "https://nexus.yudream.online/repository/plugin-store-releases/index.json";
    private long storeConnectTimeoutMillis = 5_000;
    private long storeRequestTimeoutMillis = 5_000;
    private long storeMaxResponseBytes = 1_048_576;
    private long storeMaxJarBytes = 104_857_600;
}
