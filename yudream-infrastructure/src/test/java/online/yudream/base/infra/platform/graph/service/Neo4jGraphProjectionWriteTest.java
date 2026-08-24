package online.yudream.base.infra.platform.graph.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.domain.platform.graph.valobj.GraphProjection;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionNode;
import online.yudream.base.domain.platform.graph.valobj.GraphProjectionRelationship;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;

class Neo4jGraphProjectionWriteTest {
    @Test
    void writesRealFailureShapeInOneTransactionWithFixedBatches() {
        ProjectionDriverStub stub = new ProjectionDriverStub(false);
        Neo4jGraphDatabaseGateway gateway = gateway(stub);

        var result = gateway.replaceProjection(connection(), "mc-assets", projection());

        assertEquals(1, stub.transactions.get());
        assertEquals(340, result.nodeCount());
        assertEquals(919, result.relationshipCount());
        assertEquals(List.of(340), stub.nodeBatchSizes);
        assertEquals(List.of(500, 419), stub.relationshipBatchSizes);
        assertTrue(stub.cyphers.stream().allMatch(cypher -> !cypher.contains("node-0") && !cypher.contains("edge-0")));
        assertTrue(stub.cyphers.stream().filter(cypher -> cypher.startsWith("UNWIND")).allMatch(cypher -> cypher.contains("$rows")));
    }

    @Test
    void rollsBackAsRelationshipCountMismatchWhenAnEndpointBatchDoesNotFullyMatch() {
        ProjectionDriverStub stub = new ProjectionDriverStub(true);
        Neo4jGraphDatabaseGateway gateway = gateway(stub);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> gateway.replaceProjection(connection(), "mc-assets", projection()));

        assertEquals("RELATIONSHIP_COUNT_MISMATCH", Neo4jGraphDatabaseGateway.failureType(failure));
        assertFalse(failure.getMessage().contains("bolt://"));
        assertFalse(failure.getMessage().contains("MATCH ("));
        assertEquals(1, stub.transactions.get());
    }

    private static Neo4jGraphDatabaseGateway gateway(ProjectionDriverStub stub) {
        return new Neo4jGraphDatabaseGateway() {
            @Override public Session openSession() { return stub.session(); }
        };
    }

    private static GraphConnection connection() {
        return GraphConnection.builder().name("MC Assets").code("mc-assets-graph").status(GraphConnectionStatus.ACTIVE).build();
    }

    private static GraphProjection projection() {
        List<GraphProjectionNode> nodes = new ArrayList<>();
        for (int index = 0; index < 340; index++) {
            nodes.add(new GraphProjectionNode("node-" + index, "MC_ITEM", Map.of("name", "item-" + index)));
        }
        List<GraphProjectionRelationship> relationships = new ArrayList<>();
        for (int index = 0; index < 919; index++) {
            relationships.add(new GraphProjectionRelationship("edge-" + index, "REQUIRES",
                    "node-" + (index % 340), "node-" + ((index + 1) % 340), Map.of()));
        }
        return new GraphProjection("mc-assets", "1.21.1", nodes, relationships);
    }

    private static final class ProjectionDriverStub {
        private final boolean shortRelationshipBatch;
        private final AtomicInteger transactions = new AtomicInteger();
        private final List<Integer> nodeBatchSizes = new ArrayList<>();
        private final List<Integer> relationshipBatchSizes = new ArrayList<>();
        private final List<String> cyphers = new ArrayList<>();

        private ProjectionDriverStub(boolean shortRelationshipBatch) {
            this.shortRelationshipBatch = shortRelationshipBatch;
        }

        private Session session() {
            TransactionContext transaction = transaction();
            return (Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Session.class},
                    (proxy, method, arguments) -> {
                        if ("executeWrite".equals(method.getName())) {
                            transactions.incrementAndGet();
                            @SuppressWarnings("unchecked")
                            TransactionCallback<Object> callback = (TransactionCallback<Object>) arguments[0];
                            return callback.execute(transaction);
                        }
                        if ("close".equals(method.getName())) return null;
                        return defaultValue(method.getReturnType());
                    });
        }

        private TransactionContext transaction() {
            return (TransactionContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{TransactionContext.class},
                    (proxy, method, arguments) -> {
                        if (!"run".equals(method.getName())) return defaultValue(method.getReturnType());
                        String cypher = (String) arguments[0];
                        cyphers.add(cypher);
                        @SuppressWarnings("unchecked")
                        Map<String, Object> parameters = arguments.length > 1 && arguments[1] instanceof Map<?, ?>
                                ? (Map<String, Object>) arguments[1] : Map.of();
                        long total = total(cypher, parameters);
                        return result(total);
                    });
        }

        private long total(String cypher, Map<String, Object> parameters) {
            if (cypher.startsWith("UNWIND")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rows = (List<Map<String, Object>>) parameters.get("rows");
                if (cypher.contains("PluginGraphProjectionRelation")) {
                    relationshipBatchSizes.add(rows.size());
                    return shortRelationshipBatch ? rows.size() - 1L : rows.size();
                }
                nodeBatchSizes.add(rows.size());
                return rows.size();
            }
            if (cypher.contains("RETURN count(n)")) return 340;
            if (cypher.contains("RETURN count(r)")) return 919;
            return 0;
        }

        private Result result(long total) {
            org.neo4j.driver.Record record = (org.neo4j.driver.Record) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{org.neo4j.driver.Record.class},
                    (proxy, method, arguments) -> "get".equals(method.getName()) ? Values.value(total) : defaultValue(method.getReturnType()));
            return (Result) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Result.class},
                    (proxy, method, arguments) -> "single".equals(method.getName()) ? record : defaultValue(method.getReturnType()));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
