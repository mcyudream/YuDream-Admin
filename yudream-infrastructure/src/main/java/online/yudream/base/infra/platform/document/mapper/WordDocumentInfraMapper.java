package online.yudream.base.infra.platform.document.mapper;

import online.yudream.base.domain.platform.document.aggregate.WordGenerationRecord;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;
import online.yudream.base.infra.platform.document.dataobj.WordGenerationRecordDO;
import online.yudream.base.infra.platform.document.dataobj.WordTemplateDO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WordDocumentInfraMapper {

    private static final String DOT = ".";
    private static final String ESCAPED_DOT = "．";

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
        dataObj.setPlaceholders(escapeStringMapKeys(domain.getPlaceholders()));
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
                .placeholders(restoreStringMapKeys(dataObj.getPlaceholders()))
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
        dataObj.setData(escapeMapKeys(domain.getData()));
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
                .data(restoreMapKeys(dataObj.getData()))
                .status(dataObj.getStatus())
                .errorMessage(dataObj.getErrorMessage())
                .operatorId(dataObj.getOperatorId())
                .generatedAt(dataObj.getGeneratedAt())
                .build();
    }

    private static Map<String, String> escapeStringMapKeys(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(escapeKey(key), value));
        return result;
    }

    private static Map<String, String> restoreStringMapKeys(Map<String, String> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(restoreKey(key), value));
        return result;
    }

    private static Map<String, Object> escapeMapKeys(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(escapeKey(key), escapeValue(value)));
        return result;
    }

    private static Map<String, Object> restoreMapKeys(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        source.forEach((key, value) -> result.put(restoreKey(key), restoreValue(value)));
        return result;
    }

    private static Object escapeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> nested.put(escapeKey(String.valueOf(key)), escapeValue(item)));
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> nested = new ArrayList<>(list.size());
            list.forEach(item -> nested.add(escapeValue(item)));
            return nested;
        }
        return value;
    }

    private static Object restoreValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> nested.put(restoreKey(String.valueOf(key)), restoreValue(item)));
            return nested;
        }
        if (value instanceof List<?> list) {
            List<Object> nested = new ArrayList<>(list.size());
            list.forEach(item -> nested.add(restoreValue(item)));
            return nested;
        }
        return value;
    }

    private static String escapeKey(String key) {
        return key == null ? "" : key.replace(DOT, ESCAPED_DOT);
    }

    private static String restoreKey(String key) {
        return key == null ? "" : key.replace(ESCAPED_DOT, DOT);
    }
}
