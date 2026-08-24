package online.yudream.base.application.platform.graph.assembler;

import online.yudream.base.application.platform.graph.dto.GraphConnectionDTO;
import online.yudream.base.application.platform.graph.dto.GraphQueryLogDTO;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.aggregate.GraphQueryLog;

public class GraphAssembler {
    private GraphAssembler() { }
    public static GraphConnectionDTO toDTO(GraphConnection table) {
        return GraphConnectionDTO.builder().id(table.getId()).name(table.getName()).code(table.getCode())
                .description(table.getDescription()).status(table.getStatus()).authorizedPluginCodes(table.normalizedAuthorizedPluginCodes())
                .createTime(table.getCreateTime()).updateTime(table.getUpdateTime()).build();
    }
    public static GraphQueryLogDTO toDTO(GraphQueryLog log) {
        return GraphQueryLogDTO.builder().id(log.getId()).tableId(log.getTableId()).tableCode(log.getTableCode())
                .cypher(log.getCypher()).params(log.getParams()).rows(log.getRows()).summary(log.getSummary())
                .durationMillis(log.getDurationMillis()).status(log.getStatus()).errorMessage(log.getErrorMessage())
                .executedAt(log.getExecutedAt()).build();
    }
}
