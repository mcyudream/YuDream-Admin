package online.yudream.base.application.platform.form.assembler;

import online.yudream.base.application.platform.form.dto.DynamicFormDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionDTO;
import online.yudream.base.domain.platform.form.aggregate.DynamicForm;
import online.yudream.base.domain.platform.form.aggregate.FormSubmission;

public class DynamicFormAssembler {

    private DynamicFormAssembler() {
    }

    public static DynamicFormDTO toDTO(DynamicForm form) {
        if (form == null) {
            return null;
        }
        return DynamicFormDTO.builder()
                .id(form.getId())
                .name(form.getName())
                .code(form.getCode())
                .description(form.getDescription())
                .schemaJson(form.getSchemaJson())
                .optionJson(form.getOptionJson())
                .allowAnonymous(form.getAllowAnonymous())
                .status(form.getStatus())
                .publishedAt(form.getPublishedAt())
                .createTime(form.getCreateTime())
                .updateTime(form.getUpdateTime())
                .build();
    }

    public static FormSubmissionDTO toDTO(FormSubmission submission) {
        if (submission == null) {
            return null;
        }
        return FormSubmissionDTO.builder()
                .id(submission.getId())
                .formId(submission.getFormId())
                .formCode(submission.getFormCode())
                .data(submission.getData())
                .submitterId(submission.getSubmitterId())
                .submitterIp(submission.getSubmitterIp())
                .submittedAt(submission.getSubmittedAt())
                .createTime(submission.getCreateTime())
                .build();
    }
}
