package online.yudream.base.application.platform.integration.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class RuntimeScriptDTO {
    private Long id;
    private String name;
    private String code;
    private RuntimeLanguage language;
    private String scriptContent;
    private int timeoutMillis;
    private Map<String, String> env;
    private ConnectorStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
