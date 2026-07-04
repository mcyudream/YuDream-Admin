package online.yudream.base.infra.platform.form.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.form.aggregate.FormSubmission;
import online.yudream.base.domain.platform.form.repo.FormSubmissionRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.form.dataobj.FormSubmissionDO;
import online.yudream.base.infra.platform.form.mapper.DynamicFormInfraMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormSubmissionRepoImpl implements FormSubmissionRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public FormSubmission save(FormSubmission submission) {
        FormSubmissionDO dataObj = DynamicFormInfraMapper.toDataObj(submission);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return DynamicFormInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public PageResult<FormSubmission> pageByFormId(Long formId, int page, int size) {
        Query query = formQuery(formId).with(Sort.by(Sort.Direction.DESC, "submittedAt", "createTime"));
        long total = mongoTemplate.count(query, FormSubmissionDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, FormSubmissionDO.class).stream().map(DynamicFormInfraMapper::toDomain).toList(),
                total,
                currentPage,
                pageSize
        );
    }

    @Override
    public List<FormSubmission> findByFormId(Long formId, int limit) {
        Query query = formQuery(formId)
                .with(Sort.by(Sort.Direction.DESC, "submittedAt", "createTime"))
                .limit(Math.max(limit, 1));
        return mongoTemplate.find(query, FormSubmissionDO.class).stream()
                .map(DynamicFormInfraMapper::toDomain)
                .toList();
    }

    @Override
    public long countByFormId(Long formId) {
        return mongoTemplate.count(formQuery(formId), FormSubmissionDO.class);
    }

    @Override
    public long countByFormIdAndSubmittedAtAfter(Long formId, LocalDateTime time) {
        Query query = formQuery(formId).addCriteria(Criteria.where("submittedAt").gte(time));
        return mongoTemplate.count(query, FormSubmissionDO.class);
    }

    private Query formQuery(Long formId) {
        return Query.query(Criteria.where("formId").is(formId));
    }
}
