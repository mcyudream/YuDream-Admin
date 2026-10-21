package online.yudream.base.application.system.template.dto;

/** 模板预检或写入过程中的可定位问题。 */
public record SystemTemplateIssueDTO(String sheet, int row, String message) {
}
