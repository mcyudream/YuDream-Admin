package online.yudream.base.domain.platform.document.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class WordTemplate extends BaseDomain {

    private String name;
    private String code;
    private Long templateFileId;
    private String originalFilename;
    private Map<String, String> placeholders;
    private String description;
    private TemplateStatus status;

    public static WordTemplate create(String name, String code, Long templateFileId, String originalFilename) {
        WordTemplate template = new WordTemplate();
        template.name = required(name, "模板名称不能为空");
        template.code = required(code, "模板编码不能为空");
        if (templateFileId == null) {
            throw new BizException("模板文件不能为空");
        }
        template.templateFileId = templateFileId;
        template.originalFilename = originalFilename;
        template.placeholders = new HashMap<>();
        template.status = TemplateStatus.ACTIVE;
        return template;
    }

    public void update(String name, Map<String, String> placeholders, String description, TemplateStatus status) {
        this.name = required(name, "模板名称不能为空");
        this.placeholders = new HashMap<>(placeholders == null ? Map.of() : placeholders);
        this.description = description;
        this.status = status == null ? TemplateStatus.ACTIVE : status;
    }

    public void replaceFile(Long templateFileId, String originalFilename) {
        if (templateFileId == null) {
            throw new BizException("模板文件不能为空");
        }
        this.templateFileId = templateFileId;
        this.originalFilename = originalFilename;
    }

    public void disable() {
        this.status = TemplateStatus.DISABLED;
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
