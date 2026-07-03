package online.yudream.base.application.platform.graph.assembler;

import online.yudream.base.application.platform.graph.dto.GraphConnectionDTO;
import online.yudream.base.application.platform.graph.dto.GraphQueryLogDTO;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.aggregate.GraphQueryLog;

public class GraphAssembler {

    private GraphAssembler() {
    }

    public static GraphConnectionDTO toDTO(GraphConnection connection) {
        return GraphConnectionDTO.builder()
                .id(connection.getId())
                .name(connection.getName())
                .code(connection.getCode())
                .uri(connection.getUri())
                .username(connection.getUsername())
                .database(connection.getDatabase())
                .status(connection.getStatus())
                .createTime(connection.getCreateTime())
                .updateTime(connection.getUpdateTime())
                .build();
    }

    public static GraphQueryLogDTO toDTO(GraphQueryLog log) {
        return GraphQueryLogDTO.builder()
                .id(log.getId())
                .connectionId(log.getConnectionId())
                .connectionCode(log.getConnectionCode())
                .cypher(log.getCypher())
                .params(log.getParams())
                .rows(log.getRows())
                .summary(log.getSummary())
                .durationMillis(log.getDurationMillis())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .executedAt(log.getExecutedAt())
                .build();
    }
}
