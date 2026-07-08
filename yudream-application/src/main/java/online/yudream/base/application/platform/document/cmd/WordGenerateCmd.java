package online.yudream.base.application.platform.document.cmd;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class WordGenerateCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long templateId;
    private Map<String, Object> data = new HashMap<>();
    private Long operatorId;
}
