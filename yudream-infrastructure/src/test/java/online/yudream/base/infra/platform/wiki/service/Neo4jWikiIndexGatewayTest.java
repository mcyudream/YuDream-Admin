package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.service.WikiEmbeddingGateway;
import online.yudream.base.domain.platform.wiki.service.WikiGraphExtractionGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiSearchHit;
import online.yudream.base.infra.platform.graph.service.Neo4jGraphDatabaseGateway;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jWikiIndexGatewayTest {

    @Test
    void searchWithoutGraphExpansionKeepsVectorHitsOnly() {
        List<WikiSearchHit> hits = gateway(false).search(space(), "部署", 5, "", false);

        assertEquals(1, hits.size());
        assertEquals(1L, hits.getFirst().nodeId());
    }

    @Test
    void searchWithGraphExpansionMergesRelatedChunksAndSkipsDuplicates() {
        List<WikiSearchHit> hits = gateway(false).search(space(), "部署", 5, "", true);

        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).nodeId());
        assertEquals(2L, hits.get(1).nodeId());
        assertTrue(hits.get(1).score() > 0.5d);
    }

    @Test
    void graphExpansionFailureKeepsVectorHits() {
        List<WikiSearchHit> hits = gateway(true).search(space(), "部署", 5, "", true);

        assertEquals(1, hits.size());
        assertEquals(1L, hits.getFirst().nodeId());
    }

    private Neo4jWikiIndexGateway gateway(boolean failExpansion) {
        WikiEmbeddingGateway embedding = (provider, model, texts) -> List.of(List.of(0.1f, 0.2f, 0.3f));
        WikiGraphExtractionGateway extractor = (provider, model, title, markdown) -> List.of();
        return new Neo4jWikiIndexGateway(embedding, extractor, tables(), new SessionGraph(session(failExpansion)));
    }

    private GraphConnectionRepo tables() {
        GraphConnection table = GraphConnection.create("Wiki", "wiki-table", "");
        return new GraphConnectionRepo() {
            @Override public GraphConnection save(GraphConnection connection) { return connection; }
            @Override public Optional<GraphConnection> findById(Long id) { return Optional.empty(); }
            @Override public Optional<GraphConnection> findByCode(String code) { return Optional.of(table); }
            @Override public PageResult<GraphConnection> page(String keyword, GraphConnectionStatus status, int page, int size) {
                return new PageResult<>(List.of(table), 1, page, size);
            }
        };
    }

    private Session session(boolean failExpansion) {
        AtomicInteger calls = new AtomicInteger();
        return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class}, (proxy, method, arguments) -> {
            if ("run".equals(method.getName())) {
                int call = calls.getAndIncrement();
                if (call == 0) {
                    return result(List.of(record(chunk(1L, "部署", "/deploy", "部署步骤"))));
                }
                if (failExpansion) {
                    throw new RuntimeException("graph expansion failed");
                }
                return result(List.of(
                        record(chunk(1L, "部署", "/deploy", "部署步骤"), 0.9d),
                        record(chunk(2L, "回滚", "/rollback", "回滚步骤"), 0.8d)
                ));
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private WikiSpace space() {
        WikiSpace space = WikiSpace.create("Demo", "demo");
        space.setId(100L);
        space.update("Demo", "demo", "", false, false, "openai", "text-embedding-3-small", true, "openai", "gpt-4o-mini", 800, 120, 8);
        space.setGraphTableCode("wiki-table");
        return space;
    }

    private static Result result(List<Record> records) {
        Iterator<Record> iterator = records.iterator();
        return (Result) Proxy.newProxyInstance(Result.class.getClassLoader(), new Class<?>[]{Result.class}, (proxy, method, arguments) -> {
            if ("hasNext".equals(method.getName())) {
                return iterator.hasNext();
            }
            if ("next".equals(method.getName())) {
                return iterator.next();
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Record record(Node chunk) {
        return record(chunk, null);
    }

    private static Record record(Node chunk, Double confidence) {
        return (Record) Proxy.newProxyInstance(Record.class.getClassLoader(), new Class<?>[]{Record.class}, (proxy, method, arguments) -> {
            if ("get".equals(method.getName()) && arguments != null && arguments.length == 1) {
                String key = String.valueOf(arguments[0]);
                if ("node".equals(key) || "chunk".equals(key)) {
                    return value(chunk);
                }
                if ("score".equals(key)) {
                    return value(0.91d);
                }
                if ("confidence".equals(key)) {
                    return value(confidence);
                }
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Node chunk(long nodeId, String title, String path, String content) {
        return (Node) Proxy.newProxyInstance(Node.class.getClassLoader(), new Class<?>[]{Node.class}, (proxy, method, arguments) -> {
            if ("get".equals(method.getName()) && arguments != null && arguments.length == 1) {
                return switch (String.valueOf(arguments[0])) {
                    case "nodeId" -> value(nodeId);
                    case "title" -> value(title);
                    case "path" -> value(path);
                    case "content" -> value(content);
                    default -> value(null);
                };
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Value value(Object payload) {
        return (Value) Proxy.newProxyInstance(Value.class.getClassLoader(), new Class<?>[]{Value.class}, (proxy, method, arguments) -> switch (method.getName()) {
            case "asNode" -> payload;
            case "asLong" -> payload instanceof Number number ? number.longValue() : 0L;
            case "asDouble" -> payload instanceof Number number ? number.doubleValue() : 0d;
            case "asString" -> payload == null ? "" : payload.toString();
            case "isNull" -> payload == null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        return 0;
    }

    private static final class SessionGraph extends Neo4jGraphDatabaseGateway {
        private final Session session;

        private SessionGraph(Session session) {
            this.session = session;
        }

        @Override
        public Session openSession() {
            return session;
        }
    }
}
