package online.yudream.base.infra.platform.integration.impl;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.platform.integration.aggregate.HttpInvocationLog;
import online.yudream.base.domain.platform.integration.repo.HttpInvocationLogRepo;
import online.yudream.base.domain.shared.IdGenerator;
import online.yudream.base.infra.platform.integration.dataobj.HttpInvocationLogDO;
import online.yudream.base.infra.platform.integration.mapper.IntegrationInfraMapper;
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
public class HttpInvocationLogRepoImpl implements HttpInvocationLogRepo {

    private final MongoTemplate mongoTemplate;
    private final IdGenerator idGenerator;

    @Override
    public HttpInvocationLog save(HttpInvocationLog log) {
        HttpInvocationLogDO dataObj = IntegrationInfraMapper.toDataObj(log);
        if (dataObj.getId() == null) {
            dataObj.setId(idGenerator.nextId());
            dataObj.setCreateTime(LocalDateTime.now());
        }
        dataObj.setUpdateTime(LocalDateTime.now());
        return IntegrationInfraMapper.toDomain(mongoTemplate.save(dataObj));
    }

    @Override
    public PageResult<HttpInvocationLog> page(String keyword, int page, int size) {
        Query query = query(keyword).with(Sort.by(Sort.Direction.DESC, "invokedAt"));
        long total = mongoTemplate.count(query, HttpInvocationLogDO.class);
        int currentPage = Math.max(page, 1);
        int pageSize = Math.max(size, 1);
        query.skip((long) (currentPage - 1) * pageSize).limit(pageSize);
        return new PageResult<>(
                mongoTemplate.find(query, HttpInvocationLogDO.class).stream().map(IntegrationInfraMapper::toDomain).toList(),
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
                    Criteria.where("connectorCode").regex(pattern, "i"),
                    Criteria.where("url").regex(pattern, "i")
            ));
        }
        return query;
    }
}
