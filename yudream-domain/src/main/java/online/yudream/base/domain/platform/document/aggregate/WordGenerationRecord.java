package online.yudream.base.domain.platform.document.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.document.enumerate.GenerationStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class WordGenerationRecord extends BaseDomain {

    private Long templateId;
    private String templateCode;
    private Long outputFileId;
    private String outputFilename;
    private Map<String, Object> data;
    private GenerationStatus status;
    private String errorMessage;
    private Long operatorId;
    private LocalDateTime generatedAt;

    public static WordGenerationRecord success(Long templateId, String templateCode, Long outputFileId,
                                                String outputFilename, Map<String, Object> data, Long operatorId) {
        return WordGenerationRecord.builder()
                .templateId(templateId)
                .templateCode(templateCode)
                .outputFileId(outputFileId)
                .outputFilename(outputFilename)
                .data(new HashMap<>(data == null ? Map.of() : data))
                .status(GenerationStatus.SUCCESS)
                .operatorId(operatorId)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public static WordGenerationRecord failed(Long templateId, String templateCode, Map<String, Object> data,
                                              String errorMessage, Long operatorId) {
        return WordGenerationRecord.builder()
                .templateId(templateId)
                .templateCode(templateCode)
                .data(new HashMap<>(data == null ? Map.of() : data))
                .status(GenerationStatus.FAILED)
                .errorMessage(errorMessage)
                .operatorId(operatorId)
                .generatedAt(LocalDateTime.now())
                .build();
    }
}
