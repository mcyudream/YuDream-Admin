package online.yudream.base.application.system.template.dto;

import java.util.List;

/** 系统数据模板包导入结果，不泄露密码、令牌或其他凭据。 */
public record SystemTemplateImportResultDTO(
        int total,
        int success,
        int failed,
        int created,
        int updated,
        int skipped,
        List<SystemTemplateIssueDTO> errors,
        List<SystemTemplateIssueDTO> warnings
) {
}
