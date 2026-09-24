package online.yudream.base.infra.system.about;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 关于系统页的最新版本探测配置：面向 Nexus 公共仓库的只读出站查询。
 */
@Data
@Component
@ConfigurationProperties(prefix = "yudream.system.about.version-check")
public class AboutVersionCheckProperties {

    /** 关闭后关于页只展示当前版本，不发出任何出站请求。 */
    private boolean enabled = true;
    private String nexusBaseUrl = "https://nexus.yudream.online";
    private long connectTimeoutMillis = 3_000;
    private long requestTimeoutMillis = 8_000;
    private long cacheTtlSeconds = 600;
    private long maxResponseBytes = 524_288;
}
