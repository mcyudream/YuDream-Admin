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
    /**
     * 插件流式 HTTP 请求体大小上限（字节），默认 10GiB（与 multipart max-request-size 对齐）。
     * 宿主在读取任何请求体字节前按 Content-Length 前置拒绝，并在边读边计数超限时抛出 413；
     * 配置为 0 或负数表示显式关闭限制（不建议）。
     */
    private long httpStreamingMaxBodyBytes = 10L * 1024 * 1024 * 1024;
}
