package online.yudream.base.infra.system.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yudream.storage.s3")
public class S3StorageProperties {

    private String endpoint = "http://localhost:9000";

    private String accessKey = "rustfs";

    private String secretKey = "rustfs";

    private String bucket = "yudream-admin";

    private String region = "us-east-1";

    private boolean pathStyleAccess = true;
}
