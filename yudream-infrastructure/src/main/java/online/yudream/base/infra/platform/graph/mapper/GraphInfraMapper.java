package online.yudream.base.infra.platform.graph.mapper;

import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.aggregate.GraphQueryLog;
import online.yudream.base.infra.platform.graph.dataobj.GraphConnectionDO;
import online.yudream.base.infra.platform.graph.dataobj.GraphQueryLogDO;

public class GraphInfraMapper {

    private GraphInfraMapper() {
    }

    public static GraphConnectionDO toDataObj(GraphConnection domain) {
        if (domain == null) {
            return null;
        }
        GraphConnectionDO dataObj = new GraphConnectionDO();
        dataObj.setId(domain.getId());
        dataObj.setName(domain.getName());
        dataObj.setCode(domain.getCode());
        dataObj.setUri(domain.getUri());
        dataObj.setUsername(domain.getUsername());
        dataObj.setPassword(domain.getPassword());
        dataObj.setDatabase(domain.getDatabase());
        dataObj.setStatus(domain.getStatus());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        return dataObj;
    }

    public static GraphConnection toDomain(GraphConnectionDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return GraphConnection.builder()
                .id(dataObj.getId())
                .name(dataObj.getName())
                .code(dataObj.getCode())
                .uri(dataObj.getUri())
                .username(dataObj.getUsername())
                .password(dataObj.getPassword())
                .database(dataObj.getDatabase())
                .status(dataObj.getStatus())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    public static GraphQueryLogDO toDataObj(GraphQueryLog domain) {
        if (domain == null) {
            return null;
        }
        GraphQueryLogDO dataObj = new GraphQueryLogDO();
        dataObj.setId(domain.getId());
        dataObj.setConnectionId(domain.getConnectionId());
        dataObj.setConnectionCode(domain.getConnectionCode());
        dataObj.setCypher(domain.getCypher());
        dataObj.setParams(domain.getParams());
        dataObj.setRows(domain.getRows());
        dataObj.setSummary(domain.getSummary());
        dataObj.setDurationMillis(domain.getDurationMillis());
        dataObj.setStatus(domain.getStatus());
        dataObj.setErrorMessage(domain.getErrorMessage());
        dataObj.setExecutedAt(domain.getExecutedAt());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        return dataObj;
    }

    public static GraphQueryLog toDomain(GraphQueryLogDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return GraphQueryLog.builder()
                .id(dataObj.getId())
                .connectionId(dataObj.getConnectionId())
                .connectionCode(dataObj.getConnectionCode())
                .cypher(dataObj.getCypher())
                .params(dataObj.getParams())
                .rows(dataObj.getRows())
                .summary(dataObj.getSummary())
                .durationMillis(dataObj.getDurationMillis())
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .executedAt(dataObj.getExecutedAt())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }
}
