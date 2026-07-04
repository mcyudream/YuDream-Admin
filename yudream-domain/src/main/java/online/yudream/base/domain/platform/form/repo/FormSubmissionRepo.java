package online.yudream.base.domain.platform.form.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.form.aggregate.FormSubmission;

import java.time.LocalDateTime;
import java.util.List;

public interface FormSubmissionRepo {

    FormSubmission save(FormSubmission submission);

    PageResult<FormSubmission> pageByFormId(Long formId, int page, int size);

    List<FormSubmission> findByFormId(Long formId, int limit);

    long countByFormId(Long formId);

    long countByFormIdAndSubmittedAtAfter(Long formId, LocalDateTime time);
}
