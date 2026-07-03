package online.yudream.base.application.platform.graph.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;

@Data
public class GraphConnectionSaveCmd {
    private Long id;
    private String name;
    private String code;
    private String uri;
    private String username;
    private String password;
    private String database;
    private GraphConnectionStatus status;
}
