package online.yudream.base.infra.platform.render.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "yudream.platform.render")
public class MessageRenderProperties {
    private String baseUrl = "http://localhost:3000";
    private String token = "";
    private Duration timeout = Duration.ofSeconds(30);
    private DataSize maxResponseSize = DataSize.ofMegabytes(16);
}
