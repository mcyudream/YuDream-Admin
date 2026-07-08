package online.yudream.base.application.platform.document.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.document.enumerate.GenerationStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class WordGenerationRecordDTO {
    private Long id;
    private Long templateId;
    private String templateCode;
    private Long outputFileId;
    private String outputFileUrl;
    private String outputFilename;
    private Map<String, Object> data;
    private GenerationStatus status;
    private String errorMessage;
    private Long operatorId;
    private LocalDateTime generatedAt;
}
