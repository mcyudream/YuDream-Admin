package online.yudream.base.infra.platform.document.mapper;

import online.yudream.base.domain.platform.document.aggregate.WordGenerationRecord;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;
import online.yudream.base.infra.platform.document.dataobj.WordGenerationRecordDO;
import online.yudream.base.infra.platform.document.dataobj.WordTemplateDO;

public class WordDocumentInfraMapper {

    private WordDocumentInfraMapper() {
    }

    public static WordTemplateDO toDataObj(WordTemplate domain) {
        if (domain == null) {
            return null;
        }
        WordTemplateDO dataObj = new WordTemplateDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setName(domain.getName());
        dataObj.setCode(domain.getCode());
        dataObj.setTemplateFileId(domain.getTemplateFileId());
        dataObj.setOriginalFilename(domain.getOriginalFilename());
        dataObj.setPlaceholders(domain.getPlaceholders());
        dataObj.setDescription(domain.getDescription());
        dataObj.setStatus(domain.getStatus());
        return dataObj;
    }

    public static WordTemplate toDomain(WordTemplateDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return WordTemplate.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .name(dataObj.getName())
                .code(dataObj.getCode())
                .templateFileId(dataObj.getTemplateFileId())
                .originalFilename(dataObj.getOriginalFilename())
                .placeholders(dataObj.getPlaceholders())
                .description(dataObj.getDescription())
                .status(dataObj.getStatus())
                .build();
    }

    public static WordGenerationRecordDO toDataObj(WordGenerationRecord domain) {
        if (domain == null) {
            return null;
        }
        WordGenerationRecordDO dataObj = new WordGenerationRecordDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setTemplateId(domain.getTemplateId());
        dataObj.setTemplateCode(domain.getTemplateCode());
        dataObj.setOutputFileId(domain.getOutputFileId());
        dataObj.setOutputFilename(domain.getOutputFilename());
        dataObj.setData(domain.getData());
        dataObj.setStatus(domain.getStatus());
        dataObj.setErrorMessage(domain.getErrorMessage());
        dataObj.setOperatorId(domain.getOperatorId());
        dataObj.setGeneratedAt(domain.getGeneratedAt());
        return dataObj;
    }

    public static WordGenerationRecord toDomain(WordGenerationRecordDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return WordGenerationRecord.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .templateId(dataObj.getTemplateId())
                .templateCode(dataObj.getTemplateCode())
                .outputFileId(dataObj.getOutputFileId())
                .outputFilename(dataObj.getOutputFilename())
                .data(dataObj.getData())
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .operatorId(dataObj.getOperatorId())
                .generatedAt(dataObj.getGeneratedAt())
                .build();
    }
}
