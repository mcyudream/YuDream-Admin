package online.yudream.base.application.platform.document.dto;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class WordTemplateDTO {
    private Long id;
    private String name;
    private String code;
    private Long templateFileId;
    private String templateFileUrl;
    private String originalFilename;
    private Map<String, String> placeholders;
    private String description;
    private TemplateStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
