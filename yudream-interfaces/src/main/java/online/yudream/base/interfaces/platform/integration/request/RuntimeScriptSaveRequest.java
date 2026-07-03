package online.yudream.base.interfaces.platform.integration.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;

import java.util.HashMap;
import java.util.Map;

@Data
public class RuntimeScriptSaveRequest {
    @NotBlank(message = "脚本名称不能为空")
    private String name;
    @NotBlank(message = "脚本编码不能为空")
    private String code;
    private RuntimeLanguage language;
    @NotBlank(message = "脚本内容不能为空")
    private String scriptContent;
    private int timeoutMillis = 10000;
    private Map<String, String> env = new HashMap<>();
    private ConnectorStatus status;
}
