package online.yudream.base.application.platform.integration.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class RuntimeScriptSaveCmd implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
    private RuntimeLanguage language;
    private String scriptContent;
    private int timeoutMillis;
    private Map<String, String> env = new HashMap<>();
    private ConnectorStatus status;
}
