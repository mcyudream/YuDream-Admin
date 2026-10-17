package online.yudream.base.infra.platform.ai.dataobj;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/** 系统工具开关（单文档）：全局总开关 + 模块开关。 */
@Data
@Document(collection = "platformAiToolSettings")
public class AiToolSettingsDO {
    @Id
    private String key;
    @Version
    private int version;
    private Boolean systemEnabled;
    private Map<String, Boolean> moduleStates;
    private LocalDateTime updateTime;
}
