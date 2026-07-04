package online.yudream.base.infra.platform.form.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.form.aggregate.DynamicForm;
import online.yudream.base.domain.platform.form.enumerate.DynamicFormStatus;
import online.yudream.base.domain.platform.form.repo.DynamicFormRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.form.dataobj.DynamicFormDO;
import online.yudream.base.infra.platform.form.mapper.DynamicFormInfraMapper;
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
public class DynamicFormRepoImpl implements DynamicFormRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public DynamicForm save(DynamicForm form) {
        DynamicFormDO dataObj = DynamicFormInfraMapper.toDataObj(form);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return DynamicFormInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<DynamicForm> findById(Long id) {
        return Optional.ofNullable(DynamicFormInfraMapper.toDomain(mongoTemplate.findById(id, DynamicFormDO.class)));
    }

    @Override
    public Optional<DynamicForm> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(DynamicFormInfraMapper.toDomain(mongoTemplate.findOne(query, DynamicFormDO.class)));
    }

    @Override
    public void deleteById(Long id) {
        mongoTemplate.remove(Query.query(Criteria.where("id").is(id)), DynamicFormDO.class);
    }

    @Override
    public PageResult<DynamicForm> page(String keyword, DynamicFormStatus status, int page, int size) {
        Query query = query(keyword, status).with(Sort.by(Sort.Direction.DESC, "createTime"));
        long total = mongoTemplate.count(query, DynamicFormDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, DynamicFormDO.class).stream().map(DynamicFormInfraMapper::toDomain).toList(),
                total,
                currentPage,
                pageSize
        );
    }

    private Query query(String keyword, DynamicFormStatus status) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
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
