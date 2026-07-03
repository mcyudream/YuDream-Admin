package online.yudream.base.domain.platform.graph.service;

import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.valobj.GraphQueryResult;

import java.util.Map;

public interface GraphDatabaseGateway {

    GraphQueryResult test(GraphConnection connection);

    GraphQueryResult query(GraphConnection connection, String cypher, Map<String, Object> params);

    void close(String code);

    void closeAll();
}
