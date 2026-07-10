package online.yudream.base.domain.platform.integration.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeScript extends BaseDomain {

    private String name;
    private String code;
    private RuntimeLanguage language;
    private String scriptContent;
    private int timeoutMillis;
    private Map<String, String> env;
    private ConnectorStatus status;

    public static RuntimeScript create(String name, String code, RuntimeLanguage language, String scriptContent) {
        RuntimeScript script = new RuntimeScript();
        script.name = required(name, "脚本名称不能为空");
        script.code = required(code, "脚本编码不能为空");
        script.language = language == null ? RuntimeLanguage.PYTHON : language;
        script.scriptContent = required(scriptContent, "脚本内容不能为空");
        script.timeoutMillis = 10000;
        script.env = new HashMap<>();
        script.status = ConnectorStatus.ACTIVE;
        return script;
    }

    public void update(String name,
                       RuntimeLanguage language,
                       String scriptContent,
                       int timeoutMillis,
                       Map<String, String> env,
                       ConnectorStatus status) {
        this.name = required(name, "脚本名称不能为空");
        this.language = language == null ? RuntimeLanguage.PYTHON : language;
        this.scriptContent = required(scriptContent, "脚本内容不能为空");
        this.timeoutMillis = timeoutMillis <= 0 ? 10000 : timeoutMillis;
        this.env = new HashMap<>(env == null ? Map.of() : env);
        this.status = status == null ? ConnectorStatus.ACTIVE : status;
    }

    public void disable() {
        this.status = ConnectorStatus.DISABLED;
    }

    public void activate() {
        this.status = ConnectorStatus.ACTIVE;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
