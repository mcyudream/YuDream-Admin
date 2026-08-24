package online.yudream.base.application.platform.graph.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.graph.enumerate.GraphConnectionStatus;
import java.util.Set;

/** Logical graph-table save command. */
@Data
public class GraphConnectionSaveCmd {
    private Long id;
    private String name;
    private String code;
    private String description;
    private GraphConnectionStatus status;
    private Set<String> authorizedPluginCodes;
}
