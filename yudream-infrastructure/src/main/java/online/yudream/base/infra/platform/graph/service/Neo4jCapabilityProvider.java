package online.yudream.base.infra.platform.graph.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import online.yudream.base.infra.platform.capability.service.CapabilityCredentialCipher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.neo4j", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class Neo4jCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "neo4j";

    private final Neo4jGraphDatabaseGateway graphDatabaseGateway;
    private final CapabilityCredentialCipher credentialCipher;
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private volatile String credentialError;

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "Neo4j 图数据库",
                CapabilityType.GRAPH,
                "提供能力管理配置的单一 Neo4j Driver、逻辑图表诊断和查询日志能力，未启用时不会创建连接",
                "i-ri:share-circle-line",
                50,
                Map.of(
                        "uri", "bolt://localhost:7687",
                        "username", "neo4j",
                        "password", "",
                        "database", "neo4j"
                )
        );
    }

    @Override
    public CapabilityHealth health() {
        if (credentialError != null) {
            return CapabilityHealth.error(credentialError);
        }
        return enabled.get()
                ? CapabilityHealth.enabled("Neo4j 图数据库能力已启用", Map.of("driver", "lazy"))
                : CapabilityHealth.disabled("Neo4j 图数据库能力未启用");
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(false);
        String password = config == null ? null : config.get("password");
        if (credentialCipher.encrypted(password) || (password != null && !credentialCipher.canDecrypt())) {
            credentialError = "Neo4j 凭据无法解密，请配置 YUDREAM_CREDENTIAL_KEY 后保存或启用";
            throw new IllegalStateException(credentialError);
        }
        graphDatabaseGateway.reconfigure(config);
        credentialError = null;
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
        graphDatabaseGateway.closeAll();
    }

    @Override
    public CapabilityTestResult test(String message) {
        return enabled.get()
                ? CapabilityTestResult.success("Neo4j 能力已启用，请在图数据库页面诊断部署连接")
                : CapabilityTestResult.failure("Neo4j 图数据库能力未启用");
    }
}
