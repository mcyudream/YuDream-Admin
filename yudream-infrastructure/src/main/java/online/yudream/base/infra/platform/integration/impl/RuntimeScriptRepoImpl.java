package online.yudream.base.infra.platform.integration.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.integration.aggregate.RuntimeScript;
import online.yudream.base.domain.platform.integration.repo.RuntimeScriptRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.integration.dataobj.RuntimeScriptDO;
import online.yudream.base.infra.platform.integration.mapper.IntegrationInfraMapper;
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
public class RuntimeScriptRepoImpl implements RuntimeScriptRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public RuntimeScript save(RuntimeScript script) {
        RuntimeScriptDO dataObj = IntegrationInfraMapper.toDataObj(script);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return IntegrationInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public Optional<RuntimeScript> findById(Long id) {
        return Optional.ofNullable(IntegrationInfraMapper.toDomain(mongoTemplate.findById(id, RuntimeScriptDO.class)));
    }

    @Override
    public Optional<RuntimeScript> findByCode(String code) {
        Query query = Query.query(Criteria.where("code").is(code));
        return Optional.ofNullable(IntegrationInfraMapper.toDomain(mongoTemplate.findOne(query, RuntimeScriptDO.class)));
    }

    @Override
    public PageResult<RuntimeScript> page(String keyword, int page, int size) {
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "createTime"));
        long total = mongoTemplate.count(query, RuntimeScriptDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, RuntimeScriptDO.class).stream().map(IntegrationInfraMapper::toDomain).toList(),
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
                    Criteria.where("code").regex(pattern, "i")
            ));
        }
        return query;
    }
}
