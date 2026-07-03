package online.yudream.base.domain.platform.document.repo;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.document.aggregate.WordGenerationRecord;

public interface WordGenerationRecordRepo {

    WordGenerationRecord save(WordGenerationRecord record);

    PageResult<WordGenerationRecord> page(String keyword, int page, int size);
}
