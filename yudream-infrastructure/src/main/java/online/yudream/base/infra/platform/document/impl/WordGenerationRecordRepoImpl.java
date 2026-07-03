package online.yudream.base.infra.platform.document.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.document.aggregate.WordGenerationRecord;
import online.yudream.base.domain.platform.document.repo.WordGenerationRecordRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.document.dataobj.WordGenerationRecordDO;
import online.yudream.base.infra.platform.document.mapper.WordDocumentInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WordGenerationRecordRepoImpl implements WordGenerationRecordRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public WordGenerationRecord save(WordGenerationRecord record) {
        WordGenerationRecordDO dataObj = WordDocumentInfraMapper.toDataObj(record);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return WordDocumentInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public PageResult<WordGenerationRecord> page(String keyword, int page, int size) {
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "generatedAt"));
        long total = mongoTemplate.count(query, WordGenerationRecordDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, WordGenerationRecordDO.class).stream().map(WordDocumentInfraMapper::toDomain).toList(),
                total,
                currentPage,
                pageSize
        );
    }

    private Query query(String keyword) {
        Query query = new Query();
        if (StringUtils.hasText(keyword)) {
            String pattern = ".*" + Pattern.quote(keyword.trim()) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("templateCode").regex(pattern, "i"),
                    Criteria.where("outputFilename").regex(pattern, "i"),
                    Criteria.where("errorMessage").regex(pattern, "i")
            ));
        }
        return query;
    }
}
