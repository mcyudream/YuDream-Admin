package online.yudream.base.infra.platform.form.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformDynamicForm")
public class DynamicFormDO extends BaseDO {
    private String name;
    @Indexed(unique = true)
    private String code;
    private String description;
    private String schemaJson;
    private String optionJson;
    private Boolean allowAnonymous;
    private DynamicFormStatus status;
    private LocalDateTime publishedAt;
}
