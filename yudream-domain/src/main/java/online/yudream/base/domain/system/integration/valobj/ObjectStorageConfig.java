package online.yudream.base.domain.system.integration.valobj;

/**
 * 对象存储配置（运行时有效值，密钥为明文）。
 * 来源可以是系统配置入库值，也可以是环境变量兜底值，由解析方保证优先级。
 */
public record ObjectStorageConfig(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        String region,
        boolean pathStyleAccess
) {

    public ObjectStorageConfig {
        endpoint = endpoint == null ? "" : endpoint.trim();
        accessKey = accessKey == null ? "" : accessKey.trim();
        secretKey = secretKey == null ? "" : secretKey;
        bucket = bucket == null ? "" : bucket.trim();
        region = region == null || region.isBlank() ? "us-east-1" : region.trim();
    }

    /** 是否已具备可连接配置（至少要有 endpoint）。 */
    public boolean configured() {
        return !endpoint.isBlank();
    }
}
