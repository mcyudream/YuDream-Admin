package online.yudream.base.domain.platform.document.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;

import java.util.Optional;

public interface WordTemplateRepo {

    WordTemplate save(WordTemplate template);

    Optional<WordTemplate> findById(Long id);

    Optional<WordTemplate> findByCode(String code);

    PageResult<WordTemplate> page(String keyword, int page, int size);
}
