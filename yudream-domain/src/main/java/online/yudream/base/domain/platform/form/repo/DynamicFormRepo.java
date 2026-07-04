package online.yudream.base.domain.platform.form.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.form.aggregate.DynamicForm;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;

import java.util.Optional;

public interface DynamicFormRepo {

    DynamicForm save(DynamicForm form);

    Optional<DynamicForm> findById(Long id);

    Optional<DynamicForm> findByCode(String code);

    void deleteById(Long id);

    PageResult<DynamicForm> page(String keyword, DynamicFormStatus status, int page, int size);
}
