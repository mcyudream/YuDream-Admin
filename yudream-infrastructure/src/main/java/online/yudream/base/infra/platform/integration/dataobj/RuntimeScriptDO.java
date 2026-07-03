package online.yudream.base.infra.platform.integration.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.integration.enumerate.ConnectorStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformRuntimeScript")
public class RuntimeScriptDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String code;
    private RuntimeLanguage language;
    private String scriptContent;
    private int timeoutMillis;
    private Map<String, String> env;
    private ConnectorStatus status;
}
