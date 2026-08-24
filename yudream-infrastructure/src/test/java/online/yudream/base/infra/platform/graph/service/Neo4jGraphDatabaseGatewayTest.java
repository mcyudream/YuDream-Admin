package online.yudream.base.infra.platform.graph.service;

import online.yudream.base.domain.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Neo4jGraphDatabaseGatewayTest {

    @Test
    void reconfigureClosesExistingLazyDriverWithoutOpeningANewOne() throws Exception {
        Neo4jGraphDatabaseGateway gateway = new Neo4jGraphDatabaseGateway();
        AtomicInteger closeCalls = new AtomicInteger();
        setDriver(gateway, driver(closeCalls));

        gateway.reconfigure(config());

        assertEquals(1, closeCalls.get());
        assertNull(driverOf(gateway));
    }

    @Test
    void rejectsUseBeforeCapabilityConnectionIsConfigured() {
        Neo4jGraphDatabaseGateway gateway = new Neo4jGraphDatabaseGateway();

        assertThrows(BizException.class, gateway::openSession);
    }

    @Test
    void rejectsIncompletePhysicalConnectionConfiguration() {
        Neo4jGraphDatabaseGateway gateway = new Neo4jGraphDatabaseGateway();

        assertThrows(BizException.class, () -> gateway.reconfigure(Map.of(
                "uri", "bolt://neo4j:7687", "username", "neo4j", "database", "neo4j")));
    }

    @Test
    void writesCompleteProjectionBatchesWithMatchedRelationshipCounts() {
        Neo4jGraphDatabaseGateway gateway = new Neo4jGraphDatabaseGateway();
        RecordingTransaction nodes = new RecordingTransaction(0);
        var nodeValues = java.util.stream.IntStream.range(0, 340)
                .mapToObj(index -> new online.yudream.base.domain.platform.graph.valobj.GraphProjectionNode(
                        "node-" + index, "ITEM", Map.of()))
                .toList();

        gateway.writeNodes(nodes.proxy(), scope(), nodeValues);

        assertEquals(List.of(340), nodes.batchSizes);
        RecordingTransaction relationships = new RecordingTransaction(0);
        var relationshipValues = java.util.stream.IntStream.range(0, 919)
                .mapToObj(index -> new online.yudream.base.domain.platform.graph.valobj.GraphProjectionRelationship(
                        "edge-" + index, "LINK", "node-0", "node-1", Map.of()))
                .toList();

        gateway.writeRelationships(relationships.proxy(), scope(), relationshipValues);

        assertEquals(List.of(500, 419), relationships.batchSizes);
        org.junit.jupiter.api.Assertions.assertTrue(relationships.cyphers.stream()
                .allMatch(cypher -> cypher.contains("MATCH (s:") && cypher.contains("MATCH (t:")
                        && cypher.contains("RETURN count(r) AS total") && !cypher.contains("}),(t:")));
    }

    @Test
    void classifiesRelationshipCountMismatchWithoutLeakingCypherOrConnectionData() {
        Neo4jGraphDatabaseGateway gateway = new Neo4jGraphDatabaseGateway();
        RecordingTransaction transaction = new RecordingTransaction(-1);
        var relationship = new online.yudream.base.domain.platform.graph.valobj.GraphProjectionRelationship(
                "edge", "LINK", "source", "target", Map.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> gateway.writeRelationships(transaction.proxy(), scope(), List.of(relationship)));

        assertEquals("RELATIONSHIP_COUNT_MISMATCH", Neo4jGraphDatabaseGateway.failureType(error));
        assertEquals("RELATIONSHIP_COUNT_MISMATCH", error.getMessage());
        var diagnostic = Neo4jGraphDatabaseGateway.failureDiagnostics(error);
        assertEquals("WRITE_RELATIONSHIPS", diagnostic.stage());
        assertEquals(0, diagnostic.batchIndex());
        assertEquals(1, diagnostic.batchSize());
        assertEquals("UNAVAILABLE", diagnostic.neo4jCode());
        assertEquals("UNAVAILABLE", diagnostic.gqlStatus());
    }

    @Test
    void capturesOnlySafeNeo4jStatusFieldsForTheFailingBatch() {
        Neo4jGraphDatabaseGateway gateway = new Neo4jGraphDatabaseGateway();
        var neo4jError = new org.neo4j.driver.exceptions.ClientException(
                "42NFF", "status-description-must-not-be-carried",
                "Neo.ClientError.Security.Forbidden", "uri password cypher params properties must-not-log",
                Map.of("diagnostic-secret", org.neo4j.driver.Values.value("must-not-log")), null);
        org.neo4j.driver.TransactionContext transaction = (org.neo4j.driver.TransactionContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{org.neo4j.driver.TransactionContext.class},
                (proxy, method, arguments) -> {
                    if ("run".equals(method.getName())) throw neo4jError;
                    return RecordingTransaction.defaultValue(method.getReturnType());
                });
        var nodes = java.util.stream.IntStream.range(0, 501)
                .mapToObj(index -> new online.yudream.base.domain.platform.graph.valobj.GraphProjectionNode(
                        "node-" + index, "ITEM", Map.of()))
                .toList();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> gateway.writeNodes(transaction, scope(), nodes));

        var diagnostic = Neo4jGraphDatabaseGateway.failureDiagnostics(error);
        assertEquals("NEO4J_WRITE_FAILED", diagnostic.failureType());
        assertEquals("WRITE_NODES", diagnostic.stage());
        assertEquals(0, diagnostic.batchIndex());
        assertEquals(500, diagnostic.batchSize());
        assertEquals("Neo.ClientError.Security.Forbidden", diagnostic.neo4jCode());
        assertEquals("42NFF", diagnostic.gqlStatus());
        assertEquals("NEO4J_WRITE_FAILED", error.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(error.getMessage().contains("must-not-log"));
    }

    private static Map<String, Object> scope() {
        return Map.of("tableCode", "table", "pluginCode", "mc-assets", "namespace", "mc-assets", "versionId", "1.21.1");
    }

    private static Map<String, String> config() {
        return Map.of("uri", "bolt://neo4j:7687", "username", "neo4j", "password", "private-password", "database", "neo4j");
    }

    private static Driver driver(AtomicInteger closeCalls) {
        return (Driver) Proxy.newProxyInstance(Neo4jGraphDatabaseGatewayTest.class.getClassLoader(), new Class<?>[]{Driver.class},
                (proxy, method, arguments) -> {
                    if ("close".equals(method.getName())) {
                        closeCalls.incrementAndGet();
                    }
                    return null;
                });
    }

    private static final class RecordingTransaction {
        private final long countDelta;
        private final List<String> cyphers = new ArrayList<>();
        private final List<Integer> batchSizes = new ArrayList<>();

        private RecordingTransaction(long countDelta) {
            this.countDelta = countDelta;
        }

        private org.neo4j.driver.TransactionContext proxy() {
            return (org.neo4j.driver.TransactionContext) Proxy.newProxyInstance(
                    Neo4jGraphDatabaseGatewayTest.class.getClassLoader(),
                    new Class<?>[]{org.neo4j.driver.TransactionContext.class},
                    (proxy, method, arguments) -> {
                        if (!"run".equals(method.getName())) return defaultValue(method.getReturnType());
                        String cypher = (String) arguments[0];
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parameters = (Map<String, Object>) arguments[1];
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> rows = (List<Map<String, Object>>) parameters.get("rows");
                        int size = rows == null ? 0 : rows.size();
                        cyphers.add(cypher);
                        batchSizes.add(size);
                        return result(size + countDelta);
                    });
        }

        private static org.neo4j.driver.Result result(long count) {
            return (org.neo4j.driver.Result) Proxy.newProxyInstance(
                    Neo4jGraphDatabaseGatewayTest.class.getClassLoader(),
                    new Class<?>[]{org.neo4j.driver.Result.class},
                    (proxy, method, arguments) -> "single".equals(method.getName()) ? record(count) : defaultValue(method.getReturnType()));
        }

        private static org.neo4j.driver.Record record(long count) {
            return (org.neo4j.driver.Record) Proxy.newProxyInstance(
                    Neo4jGraphDatabaseGatewayTest.class.getClassLoader(),
                    new Class<?>[]{org.neo4j.driver.Record.class},
                    (proxy, method, arguments) -> "get".equals(method.getName()) ? value(count) : defaultValue(method.getReturnType()));
        }

        private static org.neo4j.driver.Value value(long count) {
            return (org.neo4j.driver.Value) Proxy.newProxyInstance(
                    Neo4jGraphDatabaseGatewayTest.class.getClassLoader(),
                    new Class<?>[]{org.neo4j.driver.Value.class},
                    (proxy, method, arguments) -> "asLong".equals(method.getName()) ? count : defaultValue(method.getReturnType()));
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            return 0D;
        }
    }

    private static void setDriver(Neo4jGraphDatabaseGateway gateway, Driver driver) throws Exception {
        Field field = Neo4jGraphDatabaseGateway.class.getDeclaredField("driver");
        field.setAccessible(true);
        field.set(gateway, driver);
    }

    private static Driver driverOf(Neo4jGraphDatabaseGateway gateway) throws Exception {
        Field field = Neo4jGraphDatabaseGateway.class.getDeclaredField("driver");
        field.setAccessible(true);
        return (Driver) field.get(gateway);
    }
}
