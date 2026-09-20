package online.yudream.base.infra.installer.service;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import online.yudream.base.domain.installer.service.MongoProbe;
import online.yudream.base.domain.installer.valobj.MongoProbeResult;
import online.yudream.base.domain.installer.valobj.MongoProbeSpec;
import org.bson.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB 探针实现：基于官方驱动按需创建短连接，探测完即关闭，不产生长驻资源。
 */
@Component
public class MongoDriverProbe implements MongoProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int UNAUTHORIZED_CODE = 13;

    @Override
    public MongoProbeResult probe(MongoProbeSpec spec) {
        if (spec == null || !StringUtils.hasText(spec.uri())) {
            return MongoProbeResult.unreachable("MongoDB 连接串不能为空");
        }
        long start = System.currentTimeMillis();
        try (MongoClient client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(spec.uri().trim()))
                .applyToClusterSettings(builder -> builder.serverSelectionTimeout(
                        TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .applyToSocketSettings(builder -> builder.connectTimeout(
                        TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                .build())) {
            MongoDatabase admin = client.getDatabase("admin");
            admin.runCommand(new Document("ping", 1));
            long latency = System.currentTimeMillis() - start;
            String version = null;
            try {
                version = admin.runCommand(new Document("buildInfo", 1)).getString("version");
            } catch (Exception ignored) {
                // 版本信息获取失败不影响连通性结论
            }
            return MongoProbeResult.ok(latency, version);
        } catch (MongoSecurityException e) {
            return MongoProbeResult.authFailed(System.currentTimeMillis() - start,
                    "认证失败：用户名或密码不正确");
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == UNAUTHORIZED_CODE) {
                return MongoProbeResult.authRequired(System.currentTimeMillis() - start,
                        "服务器已启用认证，请在连接串中提供账号密码");
            }
            return MongoProbeResult.unreachable("服务器返回错误：" + e.getMessage());
        } catch (MongoTimeoutException e) {
            return MongoProbeResult.unreachable("连接超时，无法访问目标 MongoDB");
        } catch (MongoSocketException e) {
            return MongoProbeResult.unreachable("网络不可达：" + e.getMessage());
        } catch (IllegalArgumentException e) {
            return MongoProbeResult.unreachable("连接串格式不正确");
        } catch (Exception e) {
            return MongoProbeResult.unreachable("连接失败：" + e.getMessage());
        }
    }
}
