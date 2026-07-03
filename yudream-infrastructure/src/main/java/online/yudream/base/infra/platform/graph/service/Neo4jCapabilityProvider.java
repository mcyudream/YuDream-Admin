package online.yudream.base.infra.platform.graph.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class Neo4jCapabilityProvider implements CapabilityProvider {

    public static final String CODE = "neo4j";

    private final AtomicBoolean enabled = new AtomicBoolean(false);

    @Override
    public CapabilityDescriptor descriptor() {
        return new CapabilityDescriptor(
                CODE,
                "Neo4j 图数据库",
                CapabilityType.GRAPH,
                "提供可选图数据库连接、Cypher 查询和查询日志能力",
                "i-ri:share-circle-line",
                50,
                Map.of(
                        "uri", "bolt://localhost:7687",
                        "username", "neo4j",
                        "database", "neo4j"
                )
        );
    }

    @Override
    public CapabilityHealth health() {
        return enabled.get()
                ? CapabilityHealth.enabled("Neo4j 图数据库能力已启用", Map.of("driver", "lazy"))
                : CapabilityHealth.disabled("Neo4j 图数据库能力未启用");
    }

    @Override
    public void enable(Map<String, String> config) {
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    @Override
    public CapabilityTestResult test(String message) {
        return enabled.get()
                ? CapabilityTestResult.success("Neo4j 能力已启用，连接请在图数据库页面测试")
                : CapabilityTestResult.failure("Neo4j 图数据库能力未启用");
    }
}
