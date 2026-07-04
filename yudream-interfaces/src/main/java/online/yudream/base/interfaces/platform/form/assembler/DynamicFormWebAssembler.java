package online.yudream.base.interfaces.platform.form.assembler;

import online.yudream.base.application.platform.form.cmd.DynamicFormSaveCmd;
import online.yudream.base.application.platform.form.cmd.FormSubmitCmd;
import online.yudream.base.application.platform.form.dto.DynamicFormDTO;
import online.yudream.base.application.platform.form.dto.FormFieldStatDTO;
import online.yudream.base.application.platform.form.dto.FormStatisticsDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionDTO;
import online.yudream.base.application.platform.form.dto.FormValueCountDTO;
import online.yudream.base.application.platform.form.query.FormSubmissionPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.form.request.DynamicFormSaveRequest;
import online.yudream.base.interfaces.platform.form.request.FormSubmitRequest;
import online.yudream.base.interfaces.platform.form.res.DynamicFormRes;
import online.yudream.base.interfaces.platform.form.res.FormFieldStatRes;
import online.yudream.base.interfaces.platform.form.res.FormStatisticsRes;
import online.yudream.base.interfaces.platform.form.res.FormSubmissionRes;
import online.yudream.base.interfaces.platform.form.res.FormValueCountRes;

import java.util.List;

public class DynamicFormWebAssembler {

    private DynamicFormWebAssembler() {
    }

    public static DynamicFormSaveCmd toCmd(DynamicFormSaveRequest request) {
        return toCmd(null, request);
    }

    public static DynamicFormSaveCmd toCmd(Long id, DynamicFormSaveRequest request) {
        DynamicFormSaveCmd cmd = new DynamicFormSaveCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setCode(request.getCode());
        cmd.setDescription(request.getDescription());
        cmd.setSchemaJson(request.getSchemaJson());
        cmd.setOptionJson(request.getOptionJson());
        cmd.setAllowAnonymous(request.getAllowAnonymous());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static FormSubmitCmd toCmd(String code, FormSubmitRequest request, Long submitterId, String submitterIp) {
        FormSubmitCmd cmd = new FormSubmitCmd();
        cmd.setCode(code);
        cmd.setData(request == null ? null : request.getData());
        cmd.setSubmitterId(submitterId);
        cmd.setSubmitterIp(submitterIp);
        return cmd;
    }

    public static FormSubmissionPageQuery toSubmissionQuery(Long formId, FormSubmissionPageQuery query) {
        FormSubmissionPageQuery target = new FormSubmissionPageQuery();
        if (query != null) {
            target.setPage(query.getPage());
            target.setSize(query.getSize());
        }
        target.setFormId(formId);
        return target;
    }

    public static PageResult<DynamicFormRes> toPage(PageResult<DynamicFormDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(DynamicFormWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<FormSubmissionRes> toSubmissionPage(PageResult<FormSubmissionDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(DynamicFormWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static DynamicFormRes toRes(DynamicFormDTO dto) {
        return DynamicFormRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .code(dto.getCode())
                .description(dto.getDescription())
                .schemaJson(dto.getSchemaJson())
                .optionJson(dto.getOptionJson())
                .allowAnonymous(dto.getAllowAnonymous())
                .status(dto.getStatus())
                .publishedAt(dto.getPublishedAt())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static FormSubmissionRes toRes(FormSubmissionDTO dto) {
        return FormSubmissionRes.builder()
                .id(dto.getId())
                .formId(dto.getFormId())
                .formCode(dto.getFormCode())
                .data(dto.getData())
                .submitterId(dto.getSubmitterId())
                .submitterIp(dto.getSubmitterIp())
                .submittedAt(dto.getSubmittedAt())
                .createTime(dto.getCreateTime())
                .build();
    }

    public static FormStatisticsRes toRes(FormStatisticsDTO dto) {
        return FormStatisticsRes.builder()
                .formId(dto.getFormId())
                .formCode(dto.getFormCode())
                .total(dto.getTotal())
                .today(dto.getToday())
                .last7Days(dto.getLast7Days())
                .fields(toFieldResList(dto.getFields()))
                .build();
    }

    private static List<FormFieldStatRes> toFieldResList(List<FormFieldStatDTO> fields) {
        return fields.stream()
                .map(field -> FormFieldStatRes.builder()
                        .field(field.getField())
                        .filled(field.getFilled())
                        .empty(field.getEmpty())
                        .topValues(toValueResList(field.getTopValues()))
                        .build())
                .toList();
    }

    private static List<FormValueCountRes> toValueResList(List<FormValueCountDTO> values) {
        return values.stream()
                .map(value -> FormValueCountRes.builder().value(value.getValue()).count(value.getCount()).build())
                .toList();
    }
}
