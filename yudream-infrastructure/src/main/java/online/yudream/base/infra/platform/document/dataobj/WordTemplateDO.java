package online.yudream.base.infra.platform.document.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformWordTemplate")
public class WordTemplateDO extends BaseDO {
    private String name;
    private String code;
    private Long templateFileId;
    private String originalFilename;
    private Map<String, String> placeholders = new HashMap<>();
    private String description;
    private TemplateStatus status;
}
