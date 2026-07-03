package online.yudream.base.application.platform.graph.cmd;

import lombok.Data;

import java.util.Map;

@Data
public class GraphQueryCmd {
    private Long connectionId;
    private String cypher;
    private Map<String, Object> params;
}
