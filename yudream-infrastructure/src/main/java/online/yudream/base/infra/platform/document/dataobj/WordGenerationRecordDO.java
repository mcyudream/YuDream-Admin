package online.yudream.base.infra.platform.document.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.document.enumerate.GenerationStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformWordGenerationRecord")
public class WordGenerationRecordDO extends BaseDO {
    private Long templateId;
    private String templateCode;
    private Long outputFileId;
    private String outputFilename;
    private Map<String, Object> data = new HashMap<>();
    private GenerationStatus status;
    private String errorMessage;
    private Long operatorId;
    private LocalDateTime generatedAt;
}
