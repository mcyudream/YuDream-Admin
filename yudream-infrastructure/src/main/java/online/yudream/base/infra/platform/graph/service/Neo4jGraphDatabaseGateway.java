package online.yudream.base.infra.platform.graph.service;

import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.enumerate.GraphQueryStatus;
import online.yudream.base.domain.platform.graph.service.GraphDatabaseGateway;
import online.yudream.base.domain.platform.graph.valobj.GraphQueryResult;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class Neo4jGraphDatabaseGateway implements GraphDatabaseGateway {

    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();

    @Override
    public GraphQueryResult test(GraphConnection connection) {
        return query(connection, "RETURN 1 AS ok", Map.of());
    }

    @Override
    public GraphQueryResult query(GraphConnection connection, String cypher, Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try (var session = driver(connection).session(SessionConfig.forDatabase(connection.getDatabase()))) {
            var result = session.run(new Query(cypher, params == null ? Map.of() : params));
            List<Map<String, Object>> rows = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                Map<String, Object> row = new LinkedHashMap<>();
                for (String key : record.keys()) {
                    row.put(key, toPlainObject(record.get(key)));
                }
                rows.add(row);
            }
            var summary = result.consume();
            return new GraphQueryResult(rows, summary.queryType().name(), elapsed(start), GraphQueryStatus.SUCCESS, null);
        } catch (Neo4jException | IllegalArgumentException ex) {
            return new GraphQueryResult(List.of(), "执行失败", elapsed(start), GraphQueryStatus.FAILED, ex.getMessage());
        }
    }

    @Override
    public void close(String code) {
        Driver driver = drivers.remove(code);
        if (driver != null) {
            driver.close();
        }
    }

    @Override
    public void closeAll() {
        drivers.values().forEach(Driver::close);
        drivers.clear();
    }

    private Driver driver(GraphConnection connection) {
        return drivers.computeIfAbsent(connection.getCode(), ignored -> {
            Driver driver = GraphDatabase.driver(connection.getUri(), AuthTokens.basic(connection.getUsername(), connection.getPassword()));
            driver.verifyConnectivity();
            return driver;
        });
    }

    private Object toPlainObject(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asObject();
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
