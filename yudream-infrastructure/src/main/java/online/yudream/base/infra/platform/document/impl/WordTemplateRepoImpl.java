package online.yudream.base.infra.platform.document.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;
import online.yudream.base.domain.platform.document.repo.WordTemplateRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.document.dataobj.WordTemplateDO;
import online.yudream.base.infra.platform.document.mapper.WordDocumentInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WordTemplateRepoImpl implements WordTemplateRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public WordTemplate save(WordTemplate template) {
        WordTemplateDO dataObj = WordDocumentInfraMapper.toDataObj(template);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return WordDocumentInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<WordTemplate> findById(Long id) {
        return Optional.ofNullable(WordDocumentInfraMapper.toDomain(mongoTemplate.findById(id, WordTemplateDO.class)));
    }

    @Override
    public Optional<WordTemplate> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(WordDocumentInfraMapper.toDomain(mongoTemplate.findOne(query, WordTemplateDO.class)));
    }

    @Override
    public PageResult<WordTemplate> page(String keyword, int page, int size) {
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "createTime"));
        long total = mongoTemplate.count(query, WordTemplateDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, WordTemplateDO.class).stream().map(WordDocumentInfraMapper::toDomain).toList(),
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
                    Criteria.where("name").regex(pattern, "i"),
                    Criteria.where("code").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")
            ));
        }
        return query;
    }
}
