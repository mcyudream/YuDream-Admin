package online.yudream.base.interfaces.platform.form.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;

import java.time.LocalDateTime;

@Data
@Builder
public class DynamicFormRes {
    private Long id;
    private String name;
    private String code;
    private String description;
    private String schemaJson;
    private String optionJson;
    private Boolean allowAnonymous;
    private DynamicFormStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
