package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class PluginHttpDispatchCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pluginCode;
    private String method;
    private String path;
    private Map<String, List<String>> headers;
    private Map<String, List<String>> query;
    private String body;
    private Long userId;
    private List<String> permissions;
}
