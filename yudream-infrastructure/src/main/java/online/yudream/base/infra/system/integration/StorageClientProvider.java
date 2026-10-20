package online.yudream.base.infra.system.integration;

import online.yudream.base.domain.system.integration.valobj.ObjectStorageConfig;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * 对象存储客户端动态提供者：按「有效存储配置」的值哈希缓存实例，
 * 配置变更时关闭旧客户端并按新配置重建，无需重启。
 */
@Component
public class StorageClientProvider {

    private final SystemIntegrationSettings settings;

    private volatile S3Client cached;
    private volatile int cachedHash = Integer.MIN_VALUE;

    public StorageClientProvider(SystemIntegrationSettings settings) {
        this.settings = settings;
    }

    public S3Client client() {
        ObjectStorageConfig config = settings.effectiveStorage();
        int hash = config.hashCode();
        if (cached == null || cachedHash != hash) {
            synchronized (this) {
                if (cached == null || cachedHash != hash) {
                    S3Client rebuilt = build(config);
                    S3Client old = cached;
                    cached = rebuilt;
                    cachedHash = hash;
                    if (old != null) {
                        old.close();
                    }
                }
            }
        }
        return cached;
    }

    public ObjectStorageConfig config() {
        return settings.effectiveStorage();
    }

    /**
     * 按给定配置构建独立客户端；候选配置探测与动态提供者共用，保证行为一致。
     * 校验和仅在操作要求时计算：SDK 2.30+ 默认的分块流式载荷 + 尾随 CRC 不被部分 S3 兼容存储
     * （如 rustfs）接受，会以 XAmzContentSHA256Mismatch 拒绝上传；对 AWS S3 本身无影响。
     */
    public static S3Client build(ObjectStorageConfig config) {
        return S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(config.pathStyleAccess())
                        .build())
                .build();
    }
}
