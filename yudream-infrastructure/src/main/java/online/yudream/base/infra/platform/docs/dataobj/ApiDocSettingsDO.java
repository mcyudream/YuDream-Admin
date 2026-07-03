package online.yudream.base.infra.platform.docs.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformApiDocSettings")
public class ApiDocSettingsDO extends BaseDO {

    @Indexed(unique = true)
    private String code;
    private boolean enabled;
    private boolean apiKeyAccessEnabled;
    private String title;
    private String description;
    private String docVersion;
    private String openApiPath;
    private String swaggerUiPath;
}
