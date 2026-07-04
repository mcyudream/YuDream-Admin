package online.yudream.base.infra.platform.form.mapper;

import online.yudream.base.domain.platform.form.aggregate.DynamicForm;
import online.yudream.base.domain.platform.form.aggregate.FormSubmission;
import online.yudream.base.infra.platform.form.dataobj.DynamicFormDO;
import online.yudream.base.infra.platform.form.dataobj.FormSubmissionDO;

public class DynamicFormInfraMapper {

    private DynamicFormInfraMapper() {
    }

    public static DynamicFormDO toDataObj(DynamicForm domain) {
        if (domain == null) {
            return null;
        }
        DynamicFormDO dataObj = new DynamicFormDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setName(domain.getName());
        dataObj.setCode(domain.getCode());
        dataObj.setDescription(domain.getDescription());
        dataObj.setSchemaJson(domain.getSchemaJson());
        dataObj.setOptionJson(domain.getOptionJson());
        dataObj.setAllowAnonymous(domain.getAllowAnonymous());
        dataObj.setStatus(domain.getStatus());
        dataObj.setPublishedAt(domain.getPublishedAt());
        return dataObj;
    }

    public static DynamicForm toDomain(DynamicFormDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return DynamicForm.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .name(dataObj.getName())
                .code(dataObj.getCode())
                .description(dataObj.getDescription())
                .schemaJson(dataObj.getSchemaJson())
                .optionJson(dataObj.getOptionJson())
                .allowAnonymous(dataObj.getAllowAnonymous())
                .status(dataObj.getStatus())
                .publishedAt(dataObj.getPublishedAt())
                .build();
    }

    public static FormSubmissionDO toDataObj(FormSubmission domain) {
        if (domain == null) {
            return null;
        }
        FormSubmissionDO dataObj = new FormSubmissionDO();
        dataObj.setId(domain.getId());
        dataObj.setVersion(domain.getVersion());
        dataObj.setCreateTime(domain.getCreateTime());
        dataObj.setUpdateTime(domain.getUpdateTime());
        dataObj.setFormId(domain.getFormId());
        dataObj.setFormCode(domain.getFormCode());
        dataObj.setData(domain.getData());
        dataObj.setSubmitterId(domain.getSubmitterId());
        dataObj.setSubmitterIp(domain.getSubmitterIp());
        dataObj.setSubmittedAt(domain.getSubmittedAt());
        return dataObj;
    }

    public static FormSubmission toDomain(FormSubmissionDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return FormSubmission.builder()
                .id(dataObj.getId())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .formId(dataObj.getFormId())
                .formCode(dataObj.getFormCode())
                .data(dataObj.getData())
                .submitterId(dataObj.getSubmitterId())
                .submitterIp(dataObj.getSubmitterIp())
                .submittedAt(dataObj.getSubmittedAt())
                .build();
    }
}
