package online.yudream.base.application.platform.form.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.form.assembler.DynamicFormAssembler;
import online.yudream.base.application.platform.form.cmd.DynamicFormSaveCmd;
import online.yudream.base.application.platform.form.cmd.FormSubmitCmd;
import online.yudream.base.application.platform.form.dto.DynamicFormDTO;
import online.yudream.base.application.platform.form.dto.FormFieldStatDTO;
import online.yudream.base.application.platform.form.dto.FormStatisticsDTO;
import online.yudream.base.application.platform.form.dto.FormSubmissionDTO;
import online.yudream.base.application.platform.form.dto.FormValueCountDTO;
import online.yudream.base.application.platform.form.query.DynamicFormPageQuery;
import online.yudream.base.application.platform.form.query.FormSubmissionPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.form.aggregate.DynamicForm;
import online.yudream.base.domain.platform.form.aggregate.FormSubmission;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;
import online.yudream.base.domain.platform.form.repo.DynamicFormRepo;
import online.yudream.base.domain.platform.form.repo.FormSubmissionRepo;
import online.yudream.base.domain.platform.form.valobj.FormCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DynamicFormAppService {

    private static final String CAPABILITY_CODE = "form";
    private static final int STAT_SUBMISSION_LIMIT = 5000;

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final DynamicFormRepo dynamicFormRepo;
    private final FormSubmissionRepo formSubmissionRepo;

    @Transactional(readOnly = true)
    public PageResult<DynamicFormDTO> page(DynamicFormPageQuery query) {
        ensureEnabled();
        PageResult<DynamicForm> page = dynamicFormRepo.page(query.getKeyword(), query.getStatus(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(DynamicFormAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional(readOnly = true)
    public DynamicFormDTO detail(Long id) {
        ensureEnabled();
        return DynamicFormAssembler.toDTO(form(id));
    }

    @Transactional
    public DynamicFormDTO save(DynamicFormSaveCmd cmd) {
        ensureEnabled();
        DynamicForm form = cmd.getId() == null ? create(cmd) : form(cmd.getId());
        String code = FormCode.of(cmd.getCode()).value();
        ensureCodeAvailable(code, form.getId());
        form.update(cmd.getName(), code, cmd.getDescription(), cmd.getSchemaJson(), cmd.getOptionJson(), cmd.getAllowAnonymous(), cmd.getStatus());
        return DynamicFormAssembler.toDTO(dynamicFormRepo.save(form));
    }

    @Transactional
    public void publish(Long id) {
        ensureEnabled();
        DynamicForm form = form(id);
        form.publish();
        dynamicFormRepo.save(form);
    }

    @Transactional
    public void unpublish(Long id) {
        ensureEnabled();
        DynamicForm form = form(id);
        form.unpublish();
        dynamicFormRepo.save(form);
    }

    @Transactional
    public void delete(Long id) {
        ensureEnabled();
        form(id);
        dynamicFormRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DynamicFormDTO publicForm(String code) {
        ensureEnabled();
        DynamicForm form = dynamicFormRepo.findByCode(FormCode.of(code).value())
                .orElseThrow(() -> new BizException("表单不存在"));
        if (form.getStatus() != DynamicFormStatus.PUBLISHED) {
            throw new BizException("表单未发布");
        }
        return DynamicFormAssembler.toDTO(form);
    }

    @Transactional
    public FormSubmissionDTO submit(FormSubmitCmd cmd) {
        ensureEnabled();
        DynamicForm form = dynamicFormRepo.findByCode(FormCode.of(cmd.getCode()).value())
                .orElseThrow(() -> new BizException("表单不存在"));
        if (!Boolean.TRUE.equals(form.getAllowAnonymous()) && cmd.getSubmitterId() == null) {
            throw new BizException("该表单需要登录后填写");
        }
        FormSubmission submission = FormSubmission.create(form, cmd.getData(), cmd.getSubmitterId(), cmd.getSubmitterIp());
        return DynamicFormAssembler.toDTO(formSubmissionRepo.save(submission));
    }

    @Transactional(readOnly = true)
    public PageResult<FormSubmissionDTO> submissions(FormSubmissionPageQuery query) {
        ensureEnabled();
        form(query.getFormId());
        PageResult<FormSubmission> page = formSubmissionRepo.pageByFormId(query.getFormId(), query.getPage(), query.getSize());
        return new PageResult<>(page.getRecords().stream().map(DynamicFormAssembler::toDTO).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    @Transactional(readOnly = true)
    public FormStatisticsDTO statistics(Long formId) {
        ensureEnabled();
        DynamicForm form = form(formId);
        long total = formSubmissionRepo.countByFormId(formId);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        List<FormSubmission> submissions = formSubmissionRepo.findByFormId(formId, STAT_SUBMISSION_LIMIT);
        return FormStatisticsDTO.builder()
                .formId(form.getId())
                .formCode(form.getCode())
                .total(total)
                .today(formSubmissionRepo.countByFormIdAndSubmittedAtAfter(formId, todayStart))
                .last7Days(formSubmissionRepo.countByFormIdAndSubmittedAtAfter(formId, weekStart))
                .fields(fieldStats(submissions, total))
                .build();
    }

    private DynamicForm create(DynamicFormSaveCmd cmd) {
        String code = FormCode.of(cmd.getCode()).value();
        ensureCodeAvailable(code, null);
        return DynamicForm.create(cmd.getName(), code);
    }

    private DynamicForm form(Long id) {
        return dynamicFormRepo.findById(id).orElseThrow(() -> new BizException("表单不存在"));
    }

    private void ensureCodeAvailable(String code, Long currentId) {
        dynamicFormRepo.findByCode(code).ifPresent(existing -> {
            if (currentId == null || !Objects.equals(currentId, existing.getId())) {
                throw new BizException("表单编码已存在");
            }
        });
    }

    private List<FormFieldStatDTO> fieldStats(List<FormSubmission> submissions, long total) {
        Map<String, List<Object>> values = new LinkedHashMap<>();
        for (FormSubmission submission : submissions) {
            Map<String, Object> data = submission.getData() == null ? Map.of() : submission.getData();
            data.forEach((key, value) -> values.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(value));
        }
        return values.entrySet().stream()
                .map(entry -> toFieldStat(entry.getKey(), entry.getValue(), total))
                .sorted(Comparator.comparing(FormFieldStatDTO::getField))
                .toList();
    }

    private FormFieldStatDTO toFieldStat(String field, List<Object> values, long total) {
        List<String> normalized = values.stream()
                .filter(value -> value != null && !String.valueOf(value).trim().isEmpty())
                .map(this::valueText)
                .toList();
        Map<String, Long> counts = normalized.stream()
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
        List<FormValueCountDTO> topValues = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> FormValueCountDTO.builder().value(entry.getKey()).count(entry.getValue()).build())
                .toList();
        return FormFieldStatDTO.builder()
                .field(field)
                .filled(normalized.size())
                .empty(Math.max(total - normalized.size(), 0))
                .topValues(topValues)
                .build();
    }

    private String valueText(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        return String.valueOf(value);
    }

    private void ensureEnabled() {
        boolean enabled = capabilityModuleRepo.findByCode(CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
        if (!enabled) {
            throw new BizException("动态表单能力未启用，请先在平台能力中启用");
        }
    }
}
