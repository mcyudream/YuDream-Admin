package online.yudream.base.application.platform.document.assembler;

import online.yudream.base.application.platform.document.dto.WordGenerationRecordDTO;
import online.yudream.base.application.platform.document.dto.WordTemplateDTO;
import online.yudream.base.domain.platform.document.aggregate.WordGenerationRecord;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;

import java.util.function.Function;

public class WordDocumentAssembler {

    private WordDocumentAssembler() {
    }

    public static WordTemplateDTO toDTO(WordTemplate template, Function<Long, String> fileUrlResolver) {
        return WordTemplateDTO.builder()
                .id(template.getId())
                .name(template.getName())
                .code(template.getCode())
                .templateFileId(template.getTemplateFileId())
                .templateFileUrl(fileUrlResolver.apply(template.getTemplateFileId()))
                .originalFilename(template.getOriginalFilename())
                .placeholders(template.getPlaceholders())
                .description(template.getDescription())
                .status(template.getStatus())
                .createTime(template.getCreateTime())
                .updateTime(template.getUpdateTime())
                .build();
    }

    public static WordGenerationRecordDTO toDTO(WordGenerationRecord record, Function<Long, String> fileUrlResolver) {
        return WordGenerationRecordDTO.builder()
                .id(record.getId())
                .templateId(record.getTemplateId())
                .templateCode(record.getTemplateCode())
                .outputFileId(record.getOutputFileId())
                .outputFileUrl(fileUrlResolver.apply(record.getOutputFileId()))
                .outputFilename(record.getOutputFilename())
                .data(record.getData())
                .status(record.getStatus())
                .errorMessage(record.getErrorMessage())
                .operatorId(record.getOperatorId())
                .generatedAt(record.getGeneratedAt())
                .build();
    }
}
