package online.yudream.base.application.platform.document.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class WordTemplateSaveCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
    private Map<String, String> placeholders = new HashMap<>();
    private String description;
    private TemplateStatus status;
}
