package online.yudream.base.domain.platform.form.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;
import online.yudream.base.domain.platform.form.valobj.FormCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DynamicForm extends BaseDomain {

    private String name;
    private String code;
    private String description;
    private String schemaJson;
    private String optionJson;
    private Boolean allowAnonymous;
    private DynamicFormStatus status;
    private LocalDateTime publishedAt;

    public static DynamicForm create(String name, String code) {
        DynamicForm form = new DynamicForm();
        form.name = required(name, "表单名称不能为空");
        form.code = FormCode.of(code).value();
        form.status = DynamicFormStatus.DRAFT;
        form.allowAnonymous = true;
        return form;
    }

    public void update(String name, String code, String description, String schemaJson, String optionJson,
                       Boolean allowAnonymous, DynamicFormStatus status) {
        this.name = required(name, "表单名称不能为空");
        this.code = FormCode.of(code).value();
        this.description = description;
        this.schemaJson = required(schemaJson, "表单设计内容不能为空");
        this.optionJson = optionJson;
        this.allowAnonymous = allowAnonymous == null || allowAnonymous;
        if (status == DynamicFormStatus.PUBLISHED) {
            publish();
        }
        else if (status == DynamicFormStatus.DISABLED) {
            disable();
        }
        else {
            this.status = DynamicFormStatus.DRAFT;
        }
    }

    public void publish() {
        if (schemaJson == null || schemaJson.trim().isEmpty()) {
            throw new BizException("请先设计表单再发布");
        }
        this.status = DynamicFormStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = LocalDateTime.now();
        }
    }

    public void unpublish() {
        this.status = DynamicFormStatus.DRAFT;
    }

    public void disable() {
        this.status = DynamicFormStatus.DISABLED;
    }

    public boolean published() {
        return status == DynamicFormStatus.PUBLISHED;
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
